---
created: 2026-05-29
tags: [adr, observability, tracing, langfuse, opentelemetry, otlp, micrometer, llm-observability, rag]
---

# ADR-015：在线 LLM Tracing 接入 LangFuse（via OpenTelemetry / OTLP）

**状态**：代码已落地、全模块编译通过；运行态待联调（LangFuse span 树渲染、prompt/completion event→attribute 映射、跨线程传播 ①②③ 实测均需起栈验证）

> 完整设计与流程图见 `docs/design-langfuse-tracing.md`。本 ADR 接续 [[decisions/adr-014-ragas-integration]] "备选方案" 中标记为"推迟二期"的 **LangFuse 自部署** 选项；与 ADR-014（离线评估）正交——本 ADR 解决**在线链路可观测性**。

## 背景

自研 trace 体系（原 ADR-009 / ADR-010 的 `agent_traces` / `agent_trace_steps` + `TraceRecorder` + `TraceFacade` + ChatClient advisor + agent 拦截器 + AOP）于 2026-05-28 经迁移 032 **整体退役**。退役后：

- 生产请求路径**无任何 trace**，agentic 多步链路、RAG 检索漏斗均不可观测
- ADR-014 的 Ragas 是**离线评估**（在 `eval_cases` 上跑 LLM-as-judge），不覆盖线上真实请求
- `production-sample` 评估数据源因 trace 退役而断流（ADR-014 C6）

需求：为 MD 问答竖井与文档入库建立**在线分布式 LLM tracing**，"一次请求 = 一棵 span 树"。

## 决策

采用 **Spring AI 自带 Micrometer Observation → OpenTelemetry → OTLP 导出 → 自部署 LangFuse** 的形态。代码侧只产 OTLP，后端是 endpoint/配置决策；**不重建任何落库的自研 trace 框架**（trace 只走 OTLP，不进业务库）。

## 关键决策

### C1. 厂商中立的 OTLP 管线，后端选自部署 LangFuse（D1+D2）

埋点用 Spring AI 内置 Micrometer Observation，经 `micrometer-tracing-bridge-otel` + OTLP 导出，后端可随时换（LangFuse / Phoenix / 未来 LangSmith）。后端选 **自部署 LangFuse**——延续 ADR-014 C1 否决 Cloud 的同一理由（中国区稳定性 + 数据出境合规），自部署在区内可控、免费。Spring Boot 的 `management.otlp.tracing.endpoint` 使用 LangFuse trace-specific endpoint `/api/public/otel/v1/traces`，并带 `Authorization: Basic base64(pk:sk)` 与 `x-langfuse-ingestion-version: 4`；通用 `/api/public/otel` 仅用于 Collector 或通用 OTLP 环境变量场景。**不重新引入** `TraceFacade` / advisor / 拦截器那套——它们当年是为了落库；现在只需 OTLP。

### C2. Agentic 满档 span 树：唯一业务根 + graph/node 子树 + LLM/tool span（D3）

底层 `ChatModel` 注入 registry 后能自动产 LLM span，但默认无业务根 / tool span，链路扁平无父子；若让 graph observation 独立开根，又可能与 service 根形成双根 trace。决策做满档，**service 入口自建唯一业务根 span**，graph/node/tool 都挂到它下面，并优先使用框架原生埋点入口（基于 `spring-ai-alibaba` 1.1.2.2 jar 字节码核实，纠正早期"整 jar 无 observation 类"的判断）：

- **tool span 用 `ToolInterceptor`**（agent-framework 原生，`ReactAgent.builder().interceptors(...)`）：around 式包住 `ToolCallHandler.call(req)`，per-tool-call 粒度，业务工具体（`searchKnowledgeBase` / `readFullSection`）**零侵入**；`ToolCallRequest` 自带 `toolName`/`arguments`/`toolCallId`/`executionContext`(含 `state`、`threadId`)。这比"在 lambda 体内手写 span"更干净，也比旧 `TraceToolInterceptor` 切入点更标准。
- **graph/node 子树用 `GraphObservationLifecycleListener`**（graph-core 自带 Micrometer 实现）：经 `CompileConfig.builder().observationRegistry(...)` / `.withLifecycleListener(...)` 装配，并通过 `GraphObservationLifecycleListener.register(executionId, rootObservation)` 或等价机制显式挂到 service 业务根下；若 `executionId` 不可稳定获取或 register 机制不可控，则自写轻量 `GraphLifecycleListener`，只产 graph/node 子 span，父上下文取当前 service root。
- 单轮（非 agentic）、agentic、stream、ingest 均在 service / worker 入口建业务根 Observation，保证 trace root 语义一致。

> **取舍**：`命中数` 等纯语义指标只在工具内部可知，`ToolInterceptor` 仅见入参/出参字符串。这类指标改落在**检索 span**（C4）内记，既精确又不污染工具体。

### C3. 跨线程上下文传播 ①+②+③（D4）

graph-core 内部 reactive、classpath 有异步 tool calling manager、`MdHybridSearchService` 用 `CompletableFuture` 并行——纯 thread-local 根 span 会与子 span 脱节成孤儿 trace。组合方案：

- **①** `Hooks.enableAutomaticContextPropagation()` + `micrometer-context-propagation` + `ObservationThreadLocalAccessor`（覆盖 reactor 内部线程上的 LLM span）
- **②** `ContextSnapshot` 替换 `MdHybridSearchService` 中 `CompletableFuture` 的 executor（对自有并行检索确定性兜底，覆盖 ForkJoinPool）。单轮与 agentic 工具都会走 `MdHybridSearchService`，所以 ② 必须与 ①③ 同批实现，不能作为"只差 tool span"后的补丁；tool span 已由 `ToolInterceptor` 在框架执行链内承接，**不再需要包 lambda**
- **③** 关闭异步 tool calling manager（tool 同步在调用线程跑）

业务上下文不再称为 low-cardinality tag：`userId` / `sessionId` 是高基数字段，使用 LangFuse 可识别的 `langfuse.user.id` / `langfuse.session.id`；`spaceId` / `modelProvider` 使用 `langfuse.trace.metadata.space_id` / `langfuse.trace.metadata.model_provider`。这些属性先挂在根 span，再优先通过本地 trace context + `SpanProcessor`（或等价 post-processing）复制到 LLM / tool / retrieval 子 span，避免只在根 span 可见导致 LangFuse observation 级过滤聚合失效；如实现时使用 OTel baggage，必须确认这些业务属性不会随下游 HTTP 请求发送给模型提供商。

### C4. 检索漏斗做满 span（D6）

给 `vector` / `keyword` / `rerank` / `parent_expansion` 四段各打手工 span（recall 数、rerank 前后条数、各段延迟）。`mdVectorStore` 是手工 bean、需补注入 `ObservationRegistry` 才有 VectorStore 自动 span。HyDE/改写不单独打（本就是 LLM span）。理由：RAG 出问题时检索漏斗的可观测性比多打 LLM span 更有诊断价值。

### C5. 正文进 trace，但必须做 MVP 级脱敏与截断（D5）

`spring.ai.chat.observations.log-prompt/log-completion/include-error-logging` 全开，但进入 trace 的正文必须先经过 MVP 级 `ObservationFilter` / `SpanProcessor` 脱敏与长度硬上限。规则至少覆盖 API Key / JWT / Bearer Token / password 字段 / email / 手机号等正则模式，并对 prompt、completion、tool 参数、tool 返回、检索上下文分别截断；喂给模型的真实内容不受影响。

自部署 + 在区内 + 访问控制只能降低数据出境与第三方可见性风险，不能替代脱敏；LangFuse、ClickHouse 备份和运维账号仍可能看到 trace 内容。旧 `TraceRedactionService` 是**按 JSON key 脱密钥**，对纯文本 prompt 不够用，本次只复用思路，不复活旧 trace 框架。
**待验证**：Spring AI 把正文写成 span event，LangFuse 认 attribute——可能 input/output 为空，先按默认跑一版再决定是否加自定义 `SpanProcessor`。

### C6. 入库管线一期也 trace，作为独立根 trace（D11）

`MdDocumentIngestionWorker` 解析 / embedding / 图片理解（DashScope 手工 span）/ 向量入库全程上报，根 span 在 worker 内开、带 `documentId`，**不挂在上传 HTTP 请求下**（异步、生命周期超出请求）；`ingestionExecutor` 虚拟线程上同样需传播 context。

### C7. 配置门禁 + always-on 采样 + 故障隔离（D7）

- 默认关，仅 OTLP traces endpoint 就绪（或 `enterprise.kb.tracing.enabled=true`）才装配；本地 / LangFuse 未起照常启动
- 采样 MVP **always-on（ratio=1.0）**，通过 Spring Boot 原生 `management.tracing.sampling.probability=${KB_TRACING_SAMPLE_RATIO:1.0}` 生效，prod 扛不住再下调
- `BatchSpanProcessor` 异步批量导出，**导出失败仅 WARN、丢 span，绝不拖垮问答**（对齐 ADR-014 "持久化失败不影响问答"）
- MVP **只导 trace 到 LangFuse**，不导 metrics

### C8. 流式端点一期纳入（D8）

`/ask/stream`（`Flux<String>`）用 **reactor 生命周期模式**：`contextWrite` 写入 Observation + subscribe 时 start + `doFinally` 时 stop；同步 `ask`/`askAgentic` 用 try-with-resources scope。理由：流式是前端默认路径，不 trace 等于最常用路径无数据。

### C9. 基础设施复用现有实例，PG 新开独立库（D10）

复用现有 **PostgreSQL / Redis / MinIO 实例**：PG 上 `CREATE DATABASE langfuse`（独立库，LangFuse 迁移不碰应用 schema，并配置独立 `DATABASE_URL`）、Redis 独立 DB index 或 key prefix、MinIO 独立 bucket。**新增 ClickHouse 容器**，trace **30 天 TTL**（可配）。净增容器：langfuse-web / langfuse-worker / clickhouse。LangFuse web / worker 必须注入 `NEXTAUTH_SECRET`、`SALT`、`ENCRYPTION_KEY`、PG、Redis、ClickHouse、S3/MinIO、`NEXTAUTH_URL` 等必需配置，禁止使用官方 compose 示例默认密钥。

## 非目标

- 不接 LangFuse Cloud（合规、稳定性，沿用 ADR-014 C1）
- 不导出 metrics 到 LangFuse（只 trace）
- 不做复杂 DLP / 语义级脱敏（一期只做正则级敏感信息脱敏 + 长度硬上限）
- 不打通 Ragas 离线评估与 LangFuse scores（推迟二期，见"影响"待办）
- 不重建落库的自研 trace（`agent_traces` 不复活）

## 影响

**正面**：

- 补回 trace 退役后生产侧"零可观测性"的缺口；agentic 链路与 RAG 检索漏斗可视
- 厂商中立 OTLP 管线，后端可换零代码改动，与 ADR-014 评估侧中立立场一致
- tool span 由框架原生 `ToolInterceptor` 产、graph/node 子树由 `GraphObservationLifecycleListener` 或自写 `GraphLifecycleListener` 挂到 service 业务根下，业务工具体零侵入，无需 hook 框架内部
- 复用现有基础设施实例，净增 3 个容器；schema 完全隔离

**成本**：

- 净增 ClickHouse 等 3 容器（运维面 + 存储），并新增 LangFuse 必需密钥、连接串、迁移和健康检查配置
- 正文进 trace + always-on 采样 → ClickHouse 存储增长，靠脱敏、长度硬上限、30d TTL 与上下文软截断控制
- 跨线程传播（①②③）实现与验证有不确定性，是本方案主要工程风险
- 正文 event→attribute 的 LangFuse 映射待验证，可能需自定义 SpanProcessor

## 备选方案

- **接 LangFuse Cloud**：合规 + 中国区稳定性 → 否决（C1）
- **复活落库自研 trace（指向 LangFuse）**：等于重建刚删的框架，违背退役决策 → 否决
- **只做自动埋点（不建根/tool span）**：agentic 链路扁平无父子，最需要看链路处恰恰最弱 → 否决（C2）
- **在 tool lambda 体内手工开 span**：污染业务工具体，且 1.1.2.2 已提供原生 `ToolInterceptor` → 否决（改用 C2 的拦截器方案）
- **入库 trace 推迟二期**：评审中决定一期纳入（C6）
- **LangFuse 隔离部署（自带 PG）**：评审中决定复用实例 + 新开库，省资源 + schema 仍隔离（C9）

## 关联

- 前置 / 评估侧：[[decisions/adr-014-ragas-integration]]（"备选方案"标记的 LangFuse 自部署即本 ADR）
- 已退役的前身：原 ADR-009 / ADR-010 自研 trace（迁移 032 退役）
- 评估目标链路：[[decisions/adr-012-markdown-structure-rag]] · [[decisions/adr-013-md-keyword-bm25]]
- AI Provider 体系：[[ai-rag/providers]]
- 完整设计与流程图：`docs/design-langfuse-tracing.md`
