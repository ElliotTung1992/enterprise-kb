---
created: 2026-05-18
tags: [adr, architecture, observability, agent-trace, replay, eval]
---

# ADR-009：Agent 工具调用 Trace、离线复现包与评估回放

**状态**：提议中（待实施）

## 背景

系统当前已经有文档摄入、普通 RAG、Agentic RAG、商城客服助手、售后 HITL、投诉升级 StateGraph 等能力，但运行期观测数据不足：

- 普通 RAG 只持久化问答文本和 citations，缺少 query rewrite、HyDE、候选召回、rerank 明细。
- Agentic RAG 的工具调用只在单次请求内内存累积，结束后无法复现每轮搜了什么、拿到什么、为什么这么答。
- 客服助手和域处理器有工具调用、HITL 中断、投诉升级等关键动作，但目前主要依赖应用日志和业务表，缺少统一 trace。
- 意图路由已有评估集设计，但缺少可从线上运行轨迹沉淀 eval case 的数据基础。

结果是：线上问题难复现，工具错调难定位，检索质量无法回放比较，答案质量缺少稳定门禁。

本 ADR 记录一次设计评审（grill-me）达成的共识。

## 决策

### 1. 建立统一 Agent Trace 体系

第一版覆盖：

- 知识库 `AgenticQnAServiceImpl` 的 `searchKnowledgeBase`。
- 商城客服旧单体路径和两层路由路径。
- 售后域 `AfterSalesDomainHandler` 的 `checkAfterSalesEligibility` / `submitAfterSalesReview`。
- 投诉域 `ComplaintDomainHandler` 的 `escalateComplaint`。
- 后续可扩展到投诉执行器的通知、赔付、结案等工具。

Trace 粒度为 **turn 级 trace + step/tool_call 级事件**：

- 一次用户消息或一次 resume 生成一个 `agent_traces`。
- 每次模型调用、路由、守卫、工具调用、HITL 中断、resume 生成一个 `agent_trace_steps`。

### 2. 采集模式：FULL_RAW，但保留止血开关

第一版采用 `FULL_RAW`，尽可能详细保存复现所需数据：

- system prompt、history、user message。
- model provider、工具 schema/描述、应用层模型输入输出。
- 工具完整入参、完整出参、异常。
- 检索候选、rerank 结果、引用片段。
- HITL metadata、tool_call_id、review/complaint 等业务对象 ID。

但必须第一版就提供配置：

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `enterprise.kb.trace.enabled` | `true` | trace 总开关 |
| `enterprise.kb.trace.full-raw-enabled` | `true` | 是否记录完整原始载荷 |
| `enterprise.kb.trace.sample-rate` | `1.0` | 采样率，1.0 表示全量 |
| `enterprise.kb.trace.max-payload-bytes` | `262144` | 单个 step/payload 上限，超限截断并标记 |
| `enterprise.kb.trace.include-history` | `true` | 是否记录 history |
| `enterprise.kb.trace.include-prompts` | `true` | 是否记录 system prompt 和工具描述 |
| `enterprise.kb.trace.include-retrieval-excerpts` | `true` | 是否记录检索片段正文 |

### 3. 存储：第一版全部落 PostgreSQL

不引入对象存储、OpenSearch、Jaeger 或 MQ。PostgreSQL 保存 trace 索引、结构化字段和 raw JSON/TEXT payload。

理由：

- 当前项目核心持久化已经是 PostgreSQL，运维成本最低。
- 复现和评估需要按 session、space、tool、status、error、时间范围查询，关系库足够。
- 第一版优先让数据可查、可导出、可用于评估；payload 膨胀后再拆对象存储。

后续若数据量增长，可把大 payload 迁移到 S3/MinIO/OSS，表里保留 URI、hash、size、truncated 标记。

### 4. 写入策略：trace 外壳同步，step/payload 异步

写入策略：

- 请求进入时同步创建 `agent_traces`，拿到 `traceId`。
- 请求正常结束或异常时同步更新 trace 状态、输出、错误、耗时。
- 工具调用、模型输入输出、检索结果、路由/守卫等 step 细节异步 best-effort 写入。
- HITL 中断 step 同步写入，因为它关系到审核恢复和复现。
- 第一版不引入 MQ，使用单独 `traceExecutor` 或复用虚拟线程执行器。

任何 trace 细节写入失败只记录 WARN，不影响用户响应。

### 5. 接入方式：显式 `TraceRecorder`，不做 AOP

第一版手工显式埋点：

- `TraceRecorder.startTrace(...)`
- `TraceRecorder.recordModelCall(...)`
- `TraceRecorder.recordToolCall(...)`
- `TraceRecorder.recordHitlInterrupt(...)`
- `TraceRecorder.completeTrace(...)`
- `TraceRecorder.failTrace(...)`

工具构建处用 helper 包装 lambda，例如 `traceTool(traceId, "searchKnowledgeBase", input -> executeSearch(...))`。

不采用 AOP 或框架级拦截。原因是工具目前分散在 `FunctionToolCallback.builder(...)` 中，显式包装最容易拿到 `sessionId`、`spaceId`、`userId`、`domain`、`toolName`、业务对象 ID 和 step 顺序。

### 6. 模型原始请求边界：应用层完整记录，不做 provider HTTP 抓包

第一版记录应用层可稳定获得的数据：

- system prompt。
- trimmed history。
- user message。
- model provider。
- 工具名、工具描述、输入类型。
- assistant final message。
- assistant tool calls（能从返回或中断中拿到时）。
- 普通 RAG token usage。
- 异常栈摘要。

不强求 provider HTTP headers/body、SDK 内部 retry payload、API key 相关报文、流式 token delta。复现目标是“模型输入输出语义等价”，不是抓包级完全重放。

### 7. 离线复现包 + 评估回放

第一版不做真实副作用重放，只做：

- 按 `traceId` 导出 replay JSON。
- 评估 harness 读取 replay JSON 或 `eval_cases`，重放无副作用链路。
- 对有副作用工具采用 mock replay：用 trace 里的历史工具输出替代真实工具执行。

禁止第一版做“拿 traceId 一键重新提交售后、投诉、赔付”的真实重放，避免重复创建业务单据。

### 8. 三类评估全部作为门禁

第一版评估覆盖三类，并都可作为硬门禁：

1. **路由/工具选择正确性**
   - 期望 domain。
   - 期望工具。
   - 禁止工具。
   - 是否漏调必要工具。

2. **检索质量**
   - 期望 document/chunk 是否出现在 candidates 或 rerank topK。
   - 最终引用是否覆盖关键证据。
   - Agent 多轮 query 是否覆盖必要概念。

3. **答案质量**
   - 是否基于证据回答。
   - 是否幻觉。
   - 是否符合业务流程话术，例如缺订单号先追问、审核中不承诺已退款。

第一版支持人工标注 + 简单断言，后续再引入 LLM-as-judge。

### 9. Eval case：PostgreSQL 管理 + JSONL 导入导出

第一版同时支持：

- `eval_cases` / `eval_runs` / `eval_run_results` 表，便于后台管理和报表。
- JSONL 导入/导出，便于进 Git、跑 CI、冻结测试集。

推荐流程：

1. 从 `agent_traces` 导出失败、低置信、HITL、投诉升级、无答案、高频问题等样本。
2. 生成 eval case 草稿。
3. 人工补标真值。
4. 冻结为 smoke/frozen eval set。
5. CI 或本地命令跑评估，三类指标过阈值才算通过。

### 10. 最小后台页面

第一版做最小可用后台页面，不做复杂标注平台：

- trace 列表：按时间、类型、sessionId、status、tool、error 过滤。
- trace 详情：展示请求、模型、prompt/history、steps、tool 入参/出参、最终答案。
- 一键导出 replay JSON。
- 一键生成 eval case 草稿。
- eval case 列表和 eval run 结果。
- 标注先允许编辑 JSON，不做精细表单。

### 11. 权限

第一版不新增角色，复用现有权限：

- 系统管理员 `ROLE_SYSTEM_ADMIN`：可跨空间查看 trace、raw payload、导出 replay 包。
- 空间管理员 `hasPermission(spaceId, 'SPACE', 'ADMIN')`：可查看本空间 trace、raw payload、导出 replay 包。
- 审核员暂不建独立角色；如需要查看审核相关 trace，先通过现有管理员权限承载。
- `VIEWER` / `EDITOR` 默认不能访问 trace 后台。

虽然第一版采用 `FULL_RAW`，页面展示和导出仍应对明显敏感 key 做遮挡，例如 password、token、apiKey、secret、authorization。

## 建议表结构

### `agent_traces`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | trace ID |
| `trace_type` | VARCHAR(50) | `AGENTIC_QA` / `CUSTOMER_ASSISTANT` / `AFTER_SALES` / `COMPLAINT` 等 |
| `session_id` | UUID | 业务会话 ID |
| `space_id` | UUID NULL | 知识空间 ID，客服全局会话可为空 |
| `user_id` | UUID NULL | 当前用户 |
| `request_id` | VARCHAR(100) NULL | HTTP/request correlation ID |
| `model_provider` | VARCHAR(50) NULL | 模型提供商 |
| `status` | VARCHAR(30) | `RUNNING` / `SUCCEEDED` / `FAILED` / `INTERRUPTED` |
| `input_text` | TEXT | 用户输入摘要或全文 |
| `output_text` | TEXT NULL | 最终回复 |
| `raw_input_json` | JSONB NULL | 完整输入快照 |
| `raw_output_json` | JSONB NULL | 完整输出快照 |
| `duration_ms` | BIGINT NULL | 总耗时 |
| `tokens_used` | INTEGER NULL | token 用量，可为空 |
| `error_type` | VARCHAR(200) NULL | 异常类型 |
| `error_message` | TEXT NULL | 异常摘要 |
| `payload_truncated` | BOOLEAN | 是否发生截断 |
| `started_at` | TIMESTAMPTZ | 开始时间 |
| `completed_at` | TIMESTAMPTZ NULL | 完成时间 |
| `created_at` | TIMESTAMPTZ | 创建时间 |

### `agent_trace_steps`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | step ID |
| `trace_id` | UUID | 关联 `agent_traces.id` |
| `step_index` | INTEGER | trace 内顺序 |
| `step_type` | VARCHAR(50) | `MODEL_CALL` / `TOOL_CALL` / `ROUTER` / `GUARD` / `HITL_INTERRUPT` / `RESUME` / `RETRIEVAL` / `RERANK` |
| `agent_name` | VARCHAR(100) NULL | Agent 名称 |
| `tool_call_id` | VARCHAR(200) NULL | LLM 生成的工具调用 ID |
| `tool_name` | VARCHAR(100) NULL | 工具名称 |
| `status` | VARCHAR(30) | `SUCCEEDED` / `FAILED` / `SKIPPED` / `INTERRUPTED` |
| `input_json` | JSONB NULL | 结构化入参 |
| `output_json` | JSONB NULL | 结构化出参 |
| `raw_payload_json` | JSONB NULL | 完整原始载荷 |
| `business_ref_type` | VARCHAR(50) NULL | `DOCUMENT` / `REVIEW_REQUEST` / `COMPLAINT` / `COMPLAINT_PLAN` 等 |
| `business_ref_id` | UUID NULL | 关联业务对象 ID |
| `duration_ms` | BIGINT NULL | step 耗时 |
| `error_type` | VARCHAR(200) NULL | 异常类型 |
| `error_message` | TEXT NULL | 异常摘要 |
| `payload_truncated` | BOOLEAN | 是否截断 |
| `created_at` | TIMESTAMPTZ | 创建时间 |

### `eval_cases`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | case ID |
| `source_trace_id` | UUID NULL | 来源 trace |
| `case_type` | VARCHAR(50) | `ROUTING_TOOL` / `RETRIEVAL` / `ANSWER` / `END_TO_END` |
| `name` | VARCHAR(200) | case 名称 |
| `dataset` | VARCHAR(100) | `smoke` / `frozen` / `regression` 等 |
| `input_json` | JSONB | 回放输入 |
| `expected_json` | JSONB | 真值和断言 |
| `mock_outputs_json` | JSONB NULL | 副作用工具 mock 输出 |
| `enabled` | BOOLEAN | 是否启用 |
| `created_by` | UUID NULL | 创建人 |
| `created_at` | TIMESTAMPTZ | 创建时间 |
| `updated_at` | TIMESTAMPTZ | 更新时间 |

### `eval_runs` / `eval_run_results`

`eval_runs` 记录一次评估任务的配置、状态、汇总指标；`eval_run_results` 记录每个 case 的通过/失败、实际输出、断言失败原因。

## 后果

**优点**：

- 线上 Agent 行为可复现、可比较、可沉淀为评估集。
- 工具错调、检索缺证据、答案幻觉都能定位到具体 step。
- 复用 PostgreSQL 和静态后台页面，第一版实现成本低。
- 为意图路由、RAG、客服工具调用建立统一评估入口。

**权衡**：

- `FULL_RAW` 会增加数据库体积和敏感数据暴露面。
- 显式埋点需要在多个 Agent/工具构建点接入，短期有重复代码。
- provider HTTP 级原始报文不采集，极少数 SDK 层问题无法完全复现。
- 异步 step 写入在进程异常退出时可能丢少量细节，但 trace 外壳仍应保留。

## 非目标

- 第一版不做生产副作用真实重放。
- 第一版不做复杂标注平台。
- 第一版不接入 OpenTelemetry/Jaeger/OpenSearch。
- 第一版不抓取 provider HTTP headers/body。
- 第一版不做 token-by-token 流式 delta 记录。

## 相关决策

- [[decisions/adr-004-agentic-rag]] — Agentic RAG 工具调用需要 trace 化。
- [[decisions/adr-006-customer-assistant-separation]] — 客服助手独立链路是 trace 重点覆盖对象。
- [[decisions/adr-007-complaint-escalation-stategraph]] — 投诉升级涉及副作用工具，复现必须 mock replay。
- [[decisions/adr-008-intent-routing-two-tier]] — 意图路由评估需要 trace 沉淀样本。

## 相关功能

- [[features/agent-trace-eval]] — 实施计划与 API/页面设计。
