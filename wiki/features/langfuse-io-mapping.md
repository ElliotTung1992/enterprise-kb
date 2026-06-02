# LangFuse Input/Output 映射

> 状态：设计定稿，Phase 1 待实现
> 设计文档：`docs/design-langfuse-io-mapping.md`
> 母体：[[features/langfuse-tracing]]（本页是其 input/output 补齐子设计）
> 架构决策：[[decisions/adr-015-langfuse-tracing]]
> 不在范围：基础设施 span 噪音过滤（见 `docs/design-langfuse-noise-filter.md`）

## 问题

[[features/langfuse-tracing]] 落地后，LangFuse 已能看到 trace 结构与 generation 结构（`kb.qa.ask`、`kb.retrieval.vector`、`chat qwen-plus`、`embedding text-embedding-v2`），但 ClickHouse `observations.input` / `observations.output` 全为 `NULL`，UI 看不到模型输入输出、检索上下文、工具参数、入库结果。**只能确认"调用发生了"，无法排障与调参。**

根因：Spring AI 自动 generation span 产出模型名 / 耗时 / 错误 / 部分 `gen_ai.*`，但当前版本**不把 prompt/completion 写进 LangFuse 可识别的 input/output 字段**。需要项目主动把业务输入输出写入 LangFuse 原生 attribute。

## LangFuse 原生 attribute 契约（D1）

统一用 LangFuse namespace，**不**用 OpenInference `input.value`/`output.value` 作主契约（LangFuse 原生 key 优先级最高、语义最直接）：

| LangFuse 字段 | OTel attribute |
|--------------|----------------|
| trace input / output | `langfuse.trace.input` / `langfuse.trace.output` |
| observation input / output | `langfuse.observation.input` / `langfuse.observation.output` |
| trace / observation metadata | `langfuse.trace.metadata.*` / `langfuse.observation.metadata.*` |

## 两阶段方案

| 阶段 | 目标 | 状态 |
|------|------|------|
| **Phase 1** | 补齐**项目自建业务 span** 的 input/output（`TracingSupport` 控制，能保证在 `Observation.stop()` 前写入，不依赖 Spring AI handler 顺序） | 本次实现 |
| **Phase 2** | 评估是否给 Spring AI 自动 generation span（`chat qwen-plus`）补 input/output——需 `ObservationHandler<ChatModelObservationContext>`，依赖 handler 执行顺序 | PoC 后决定 |

Phase 2 不确定性：handler 若晚于 Micrometer tracing handler 执行，span 已结束、attribute 写不进导出数据；流式下 `getResponse()` 未必有完整 completion；provider 行为需实测。故只做小 PoC（handler 实现 `Ordered` 早于 stop handler、只验非流式 chat、确认 `observations.input/output` 非空再铺开），不稳定就放弃、继续靠业务 span。

## 关键决策

| 编号 | 决策 |
|------|------|
| **D1** | 用 LangFuse 原生 attribute key（见上），不以 OpenInference 为主契约 |
| **D2** | 扩展 `TracingSupport.SpanBuilder` 作为唯一写入入口：`traceInput`/`traceOutputFrom`（写 `langfuse.trace.*`）、`input`/`outputFrom`（写 `langfuse.observation.*`）、`metadata`。`*OutputFrom` 在业务体返回后、observation 停止前执行；异常时不写 output 但可写错误 metadata |
| **D3** | 拆分官方 prompt/completion 日志开关：从联动 `KB_TRACING_ENABLED` 改为独立 `KB_AI_LOG_PROMPT`/`KB_AI_LOG_COMPLETION`/`KB_AI_LOG_ERROR`，**默认全关**——官方开关只把正文打进应用日志、不进 LangFuse 也不过项目脱敏；LangFuse input/output 由本设计写入逻辑负责 |
| **D4** | **embedding generation 不写 input/output**（output 是向量对人无价值，input 体量大风险高）；验收时明确允许为空 |
| **D5** | 检索 output 写**结构化摘要**（score / documentTitle / section / 截断脱敏后的 excerpt），不写完整 parent，受 `KB_TRACING_MAX_RETRIEVAL_CHARS` 控制 |
| **D6** | 入库链路写状态/统计：trace input = 文件名/documentId，trace output = `status=READY, chunks=N` 或 `FAILED, error=...`，metadata 含 document_id/filename/chunk_count/status/error（错误脱敏截断） |
| **D7** | Phase 2 只做小 PoC，不直接铺开 |

> [!key-insight] 写入收口在 TracingSupport
> 所有正文写入**必须**在 `TracingSupport` 方法内部调用 `SensitiveDataRedactor.redactAndTruncate(...)`，调用方不能绕过脱敏直接 set `langfuse.*.input/output` 高基数正文。这是脱敏的唯一收口点，与 [[features/langfuse-tracing]] 的 `SensitiveDataObservationFilter`（KeyValue 路径兜底）互补——后者**不**覆盖 span event，也不覆盖这些直写 attribute。

## 写入规格（Phase 1）

| span | input | output | metadata |
|------|-------|--------|----------|
| `kb.qa.ask` / `.agentic` / `.stream` | 用户问题 | 最终答案（stream 为聚合后答案） | userId、sessionId、spaceId、modelProvider |
| `kb.retrieval.vector` / `keyword` | retrievalQuery、topK | 命中摘要 | spaceId、hitCount |
| `kb.retrieval.rerank` | question、candidateCount | rerank 后命中摘要 | topN、hitCount |
| `kb.retrieval.parent_expansion` | childHits 数量 | parent 摘要 | parentCount |
| `gen_ai.tool.execution` | tool arguments | tool result | toolName、toolCallId、status |
| `kb.ingest.document` | filename、documentId | status、chunkCount、error | filename、documentId、status、chunkCount |
| `kb.ingest.parse` | filename、documentId | parentCount、childCount、assetCount | status |
| `kb.ingest.image_understanding` | image metadata | caption 摘要或 status | assetId、provider |

## 改动清单

| 模块 | 文件 | 改动 |
|------|------|------|
| `kb-common` | `TracingAttributes` | 新增 input/output / metadata key 常量 |
| `kb-common` | `TracingSupport` | 新增 trace/observation input/output 写入 API，内置脱敏截断 |
| `kb-app` | `application.yml` | 拆分官方 prompt/completion 日志开关，默认关 |
| `kb-app` | `SensitiveDataObservationFilter` | 更新注释：它是 KeyValue 路径兜底，不覆盖所有直写 span attribute |
| `kb-search` | `MdQnAServiceImpl` | 问答 root / stream / 检索 span 写 input/output |
| `kb-search` | `MdAgenticQnAServiceImpl` | agentic root span 写 input/output |
| `kb-search` | `MdHybridSearchServiceImpl` | vector / keyword span 写 input/output |
| `kb-search` | `TracingToolInterceptor` | tool span 写 input/output/status |
| `kb-document` | `MdDocumentIngestionWorkerImpl` | ingest span 写 input/output/metadata |
| `kb-app`（Phase 2） | `TracingConfig` | `ChatModelObservationContext` handler PoC |

## 安全要求

1. 所有进 LangFuse 的正文必经 `SensitiveDataRedactor.redactAndTruncate`。
2. 不允许调用方直接 set `langfuse.*.input/output` 高基数正文。
3. 分类上限：`KB_TRACING_MAX_PROMPT_CHARS` / `_COMPLETION_CHARS` / `_TOOL_CHARS` / `_RETRIEVAL_CHARS`。
4. 真实发给模型的 prompt/completion **不被改写**。
5. 官方 `log-prompt`/`log-completion` 默认关，避免正文绕过脱敏进应用日志。

## 验收（Phase 1）

LangFuse UI 中 `kb.qa.ask` 可见问题+答案、`kb.retrieval.*` 可见 query+命中摘要、`gen_ai.tool.execution` 可见参数+结果、`kb.ingest.document` 可见 filename/documentId/status/chunkCount；密码/Token/API Key/手机号/邮箱不明文进 LangFuse；超长正文带截断标记；tracing 关闭时业务行为不变且不创建额外 input/output。验证 SQL 查 ClickHouse `observations` 表对应 span 的 `input/output/metadata` 非空（embedding 允许为空）。

## 关联

- 母体功能：[[features/langfuse-tracing]]
- 架构决策：[[decisions/adr-015-langfuse-tracing]]
- 脱敏共享工具：`kb-common` `com.enterprise.kb.common.tracing`（`TracingSupport` / `TracingAttributes` / `SensitiveDataRedactor`）
- 下游姊妹设计：基础设施 span 噪音过滤 `docs/design-langfuse-noise-filter.md`
- 完整设计：`docs/design-langfuse-io-mapping.md`
