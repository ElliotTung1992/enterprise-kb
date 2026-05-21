# Task Plan: Agent Trace、离线复现包与评估回放

## Goal
实现一套统一的 Agent Trace、离线复现包与评估回放能力，让 Agentic RAG、客服助手、售后 HITL 和投诉升级的工具调用可追踪、可复现、可沉淀为评估门禁。

## Status
PHASE 0-6 MINIMAL CODE COMPLETE — 待本地数据库/接口运行态验收

## Current Phase
运行态验收与二期优化

---

## Success Criteria

- 每次 Agentic QA / 客服助手对话至少生成一条 `agent_traces`。
- 每次工具调用都有 `agent_trace_steps`，包含工具名、入参、出参、异常和耗时。
- HITL 中断能在 trace 详情中看到 `tool_call_id`、工具参数、`review_request` 关联。
- 能按 `traceId` 导出 replay JSON。
- 能从 trace 生成 eval case 草稿，并支持 JSONL 导入/导出。
- 能跑三类评估：路由/工具选择、检索质量、答案质量。
- trace 后台只允许管理员访问。

---

## Phase 0: 建表迁移、骨架与配置
**Status:** complete

### Scope

建立 trace/eval 的数据库和最小服务骨架，不接入业务链路。

### Tasks

- [x] 新增 Liquibase 迁移：`025-add-agent-traces-eval.sql`。
- [x] 更新 `db.changelog-master.xml` 引入迁移。
- [x] 新增 Model：`AgentTrace`、`AgentTraceStep`、`EvalCase`、`EvalRun`、`EvalRunResult`。
- [x] 新增 Mapper 接口与 XML：trace、step、eval case、eval run、eval result。
- [x] 新增配置属性：`enterprise.kb.trace.*`。
- [x] 新增 `traceExecutor` 或复用虚拟线程执行器并明确命名。
- [x] 新增 `TraceRedactionService`，先支持敏感 key 遮挡。
- [x] 添加 service/config 单测覆盖脱敏和配置默认值；Mapper SQL 已随模块编译校验。

### Verify

- [x] `mvn test -pl kb-search -am` 通过（需使用项目指定 JDK 21）。
- [ ] Mapper 集成测试能插入 trace + step 并按 `trace_id` 查询。
- [x] 配置默认值与 ADR 一致。

### Phase 2 Optimizations

- raw payload 迁移到对象存储，PostgreSQL 仅保留 URI/hash/size。
- 添加自动保留周期清理任务和清理审计。

---

## Phase 1: TraceRecorder 与 RAG 链路接入
**Status:** complete

### Scope

先覆盖知识库问答链路，验证 trace 模型能承载 prompt、检索、rerank、答案和工具调用。

### Tasks

- [x] 新增 `TraceRecorder` 接口与实现。
- [x] 实现同步 `startTrace` / `completeTrace` / `failTrace`。
- [x] 实现异步 `recordStep`、同步 `recordHitlInterrupt`。
- [x] 接入 `AgenticQnAServiceImpl`：trace 外壳、history/user、`searchKnowledgeBase` 工具、最终答案。
- [x] 接入 `AgenticQnAServiceImpl`：检索 candidates、rerank topN、citations。
- [x] 接入 `QnAServiceImpl`：query rewrite、HyDE、hybrid search candidates、rerank topK、prompt、answer、token usage。
- [x] 对 payload 做 `max-payload-bytes` 截断与 `payload_truncated` 标记。

### Verify

- [ ] 本地请求 Agentic QA 后生成 trace 和 step。
- [ ] 普通 RAG trace 中能看到 rewrite、HyDE、候选召回、rerank 和 token usage。
- [x] `mvn test -pl kb-search -am` 编译通过，采集接入不破坏既有问答测试。
- [x] trace 写入失败不影响问答响应的保护逻辑已由 `TraceRecorderImpl` 保持；仍建议补业务集成测试。

### Phase 2 Optimizations

- 抽象统一 tool wrapper，减少各 Agent 手工埋点重复。
- 增加 provider HTTP 级 payload 采集可选扩展点，但默认关闭。

---

## Phase 2: 客服助手、售后域与投诉域工具接入
**Status:** complete for minimal code

### Scope

覆盖最难复现的 ReAct 工具链和 HITL 中断链路。

### Tasks

- [x] 接入 `CustomerAssistantServiceImpl.legacyChat()`：trace 外壳、旧单体工具、HITL 中断、最终答案。
- [x] 接入 `CustomerAssistantServiceImpl.routedChat()`：guard、domain route、dispatch domain、最终答案、domain。
- [x] 接入 `AfterSalesDomainHandler`：`checkAfterSalesEligibility`、`submitAfterSalesReview`、HITL interrupt。
- [x] 接入 `ComplaintDomainHandler`：`escalateComplaint`、`complaintId`。
- [x] 记录业务对象引用：`REVIEW_REQUEST`、`COMPLAINT`。
- [x] 确认副作用工具只记录，不改变现有业务事务顺序。

### Verify

- [ ] 售后 HITL 中断 trace 可查，且包含 `tool_call_id` 和工具参数。
- [ ] 投诉升级 trace 可查，且包含 `complaintId`。
- [ ] 客服旧单体路径和两层路由路径都能生成 trace。
- [x] `mvn test -pl kb-app -am` 编译和现有测试通过。

### Phase 2 Optimizations

- 覆盖 `ComplaintExecutorServiceImpl` 中的通知、赔付、结案等工具。
- 后续新增业务域必须通过统一 trace helper 注册工具。

---

## Phase 3: Trace 查询 API 与 Replay 导出
**Status:** complete for minimal code

### Scope

让 trace 能被后台查询，并导出离线复现包。

### Tasks

- [x] 新增 `AgentTraceService`：列表、详情、step 查询。
- [x] 实现 `/api/v1/admin/traces` 列表过滤：类型、sessionId、spaceId、status。
- [x] 实现 `/api/v1/admin/traces/{traceId}` 详情。
- [x] 实现 `/api/v1/admin/traces/{traceId}/replay` 导出 replay JSON。
- [x] 实现管理员权限控制：`ROLE_SYSTEM_ADMIN`。
- [x] 客服全局 trace 若 `spaceId` 为空，仅系统管理员可访问。

### Verify

- [ ] 系统管理员可跨空间查询和导出 replay。
- [ ] 空间管理员只能查询本空间 trace。
- [ ] `VIEWER` / `EDITOR` 无法访问 trace 后台 API。
- [ ] replay JSON 包含 input、tools、steps、finalOutput。

### Phase 2 Optimizations

- 支持 replay 包版本化、差异对比和批量导出。
- 增加导出审计日志。

---

## Phase 4: Eval Case 表、JSONL 导入导出与草稿生成
**Status:** complete for minimal code

### Scope

把 trace 沉淀成可管理、可进 Git、可 CI 跑的评估样本。

### Tasks

- [x] 新增 `EvalCaseService`：CRUD、启用/禁用、dataset 过滤。
- [x] 实现从 trace 生成 eval case 草稿。
- [x] 实现 JSONL 导入：逐行校验、错误定位。
- [x] 实现 JSONL 导出：按 dataset 过滤。
- [x] 支持 `mock_outputs_json`，用于副作用工具 mock replay。
- [x] 定义 `ROUTING_TOOL` / `RETRIEVAL` / `ANSWER` / `END_TO_END` caseType 字段。

### Verify

- [ ] JSONL round-trip 无损。
- [ ] 从真实 trace 能生成 eval case 草稿。
- [ ] 无效 JSONL 行能返回明确错误，不影响已校验行。

### Phase 2 Optimizations

- 增加标注工作流、审核状态、样本分层采样和去重。
- 增加 few-shot 污染检测，避免冻结集泄露到提示词。

---

## Phase 5: Eval Replay Harness 与三类门禁
**Status:** complete for minimal code

### Scope

实现离线回放和三类硬门禁：路由/工具选择、检索质量、答案质量。

### Tasks

- [x] 新增 `EvalReplayService`。
- [x] 使用 `eval_runs` / `eval_run_results` 记录一次评估任务与逐 case 结果。
- [x] 实现 `ROUTING_TOOL` 基础评估：domain 匹配。
- [x] 实现 `RETRIEVAL` / `ANSWER` 基础文本断言：mustContain、mustNotContain。
- [x] 对副作用工具使用 mock replay，不执行真实提交/升级/赔付。
- [ ] 增加环境变量门控：`AGENT_TRACE_EVAL=true`、`AGENT_TRACE_EVAL_DATASET=smoke`。

### Verify

- [ ] smoke dataset 可跑，并写入 eval run/result。
- [ ] 失败 case 输出明确原因。
- [ ] 禁止副作用工具误调用数为 0。
- [ ] 评估失败时能作为 CI/本地命令门禁。

### Phase 2 Optimizations

- 引入 LLM-as-judge、趋势报表、模型/提示词版本对比。
- 增加失败聚类和自动候选断言建议。

---

## Phase 6: 最小后台页面
**Status:** complete for minimal code

### Scope

新增简陋但可用的 trace/eval 管理页，不做复杂标注平台。

### Tasks

- [x] 新增 `kb-app/src/main/resources/static/traces.html`。
- [x] 新增 `kb-app/src/main/resources/static/evals.html`。
- [x] trace 列表支持过滤：类型、sessionId、status。
- [x] trace 详情 tabs：Summary / Steps / Raw JSON / Replay。
- [x] eval case 列表和详情，允许编辑 JSON。
- [x] eval run 详情展示汇总指标和失败列表。
- [x] 页面内提供 Trace/Eval 管理入口。

### Verify

- [ ] 管理员可查 trace、查看 steps、导出 replay、生成 eval case。
- [ ] 非管理员无法访问页面数据。
- [ ] 页面能处理大 JSON，至少不阻塞基本查看。

### Phase 2 Optimizations

- 升级为完整观测控制台，支持聚合指标、失败聚类和权限细分。
- 增加可视化 timeline 和 step 耗时瀑布图。

---

## Cross-Cutting Decisions

| Decision | Rationale |
|----------|-----------|
| 第一版 `FULL_RAW` | 复现和评估优先，尽可能保存 prompt/history/tool input/output/retrieval details |
| 全部落 PostgreSQL | 复用现有基础设施，降低第一版运维成本 |
| trace 外壳同步，step/payload 异步 | 保证请求必有 trace，同时避免日志写入拖慢用户响应 |
| 显式 `TraceRecorder`，不做 AOP | 工具分散在 `FunctionToolCallback.builder(...)`，显式接入最容易拿到业务上下文 |
| 第一版只做 mock replay | 避免重复执行售后、投诉、赔付等生产副作用 |
| JSONL + DB 双轨 | DB 便于后台管理，JSONL 便于进 Git 和 CI |
| 不新增角色 | 复用 `ROLE_SYSTEM_ADMIN` 和空间 `ADMIN`，降低权限改造范围 |

## Open Questions

| Question | Current Recommendation |
|----------|------------------------|
| trace 保留周期是否需要第一版自动执行？ | 第一版可先提供配置和手动清理，二期加定时任务 |
| `spaceId` 为空的客服 trace 是否要补空间归属？ | 第一版仅系统管理员可看；后续再按业务会话关联空间 |
| 普通 RAG 是否第一阶段必须接入？ | 是。它能验证检索质量评估路径 |
| provider HTTP 原始 payload 是否要抓？ | 第一版不做，只记录应用层 prompt/messages/output |

## Errors Encountered

| Error | Attempt | Resolution |
|-------|---------|------------|
| `invalid target release: 21` | 1 | 使用项目规范中的 JDK 21：`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home` 后重跑 Maven |

## Reference Documents

- `wiki/decisions/adr-009-agent-trace-replay-eval.md`
- `wiki/features/agent-trace-eval.md`
- `wiki/decisions/adr-008-intent-routing-two-tier.md`
- `wiki/features/intent-routing.md`
