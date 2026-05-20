# 功能验收报告：Agent Trace、离线复现包与评估回放

## 验收对象

| 项目 | 内容 |
|------|------|
| 功能名称 | Agent Trace、离线复现包与评估回放 |
| 当前验收阶段 | Phase 1：RAG Trace 接入 |
| 验收日期 | 2026-05-20 |
| 当前结论 | Phase 0 可验收通过；Phase 1 RAG 采集代码已接入并通过模块测试，待本地数据库接口验收 |

## 验收说明

当前代码已完成 Phase 0：数据库表、配置项、模型、Mapper、基础服务、脱敏服务和测试。Phase 1 已接入 `QnAServiceImpl` 和 `AgenticQnAServiceImpl`，客服助手、售后域和投诉域仍待后续阶段接入。以下验收场景分为三类：

- **已可验收**：当前 Phase 0 已具备能力。
- **代码已实现，待接口验收**：RAG Trace 已接入代码，仍需启动本地数据库后通过接口确认落库内容。
- **待实现后验收**：Phase 2+ 接入客服/售后/投诉链路后执行。

## Phase 0 已可验收场景

### 场景 1：系统启动时创建 Trace/Eval 数据表

| 项目 | 内容 |
|------|------|
| 前置条件 | PostgreSQL 可用，应用启用 Liquibase |
| 核心代码位置 | `kb-app/src/main/resources/db/changelog/025-add-agent-traces-eval.sql`；`kb-app/src/main/resources/db/changelog/db.changelog-master.xml` |
| 操作步骤 | 启动应用或执行 Liquibase migration |
| 期望结果 | 成功创建 `agent_traces`、`agent_trace_steps`、`eval_cases`、`eval_runs`、`eval_run_results` 五张表 |
| 期望结果 | `db.changelog-master.xml` 中包含 `025-add-agent-traces-eval.sql` |
| 期望结果 | 重复启动应用不会因表已存在而失败 |
| 当前状态 | 已实现，待环境启动验收 |

### 场景 2：Trace 配置项默认值符合设计

| 项目 | 内容 |
|------|------|
| 前置条件 | 使用默认配置启动或直接实例化 `TraceProperties` |
| 核心代码位置 | `kb-search/src/main/java/com/enterprise/kb/search/config/TraceProperties.java`；`kb-app/src/main/resources/application.yml`；`kb-search/src/test/java/com/enterprise/kb/search/config/TracePropertiesTest.java` |
| 操作步骤 | 检查 `enterprise.kb.trace.*` 默认值 |
| 期望结果 | `enabled=true` |
| 期望结果 | `full-raw-enabled=true` |
| 期望结果 | `sample-rate=1.0` |
| 期望结果 | `max-payload-bytes=262144` |
| 期望结果 | `include-history=true` |
| 期望结果 | `include-prompts=true` |
| 期望结果 | `include-retrieval-excerpts=true` |
| 当前状态 | 已通过单元测试 `TracePropertiesTest` |

### 场景 3：敏感字段在 Trace 原始载荷展示/导出前被遮挡

| 项目 | 内容 |
|------|------|
| 前置条件 | `TraceRedactionService` 可用 |
| 核心代码位置 | `kb-search/src/main/java/com/enterprise/kb/search/service/TraceRedactionService.java`；`kb-search/src/main/java/com/enterprise/kb/search/service/impl/TraceRedactionServiceImpl.java`；`kb-search/src/test/java/com/enterprise/kb/search/service/impl/TraceRedactionServiceImplTest.java` |
| 操作步骤 | 输入包含 `authorization`、`apiKey`、`refresh_token` 等字段的 JSON |
| 期望结果 | 敏感字段值被替换为 `***REDACTED***` |
| 期望结果 | 非敏感字段保持原值 |
| 期望结果 | 嵌套对象和数组中的敏感字段也会被遮挡 |
| 当前状态 | 已通过单元测试 `TraceRedactionServiceImplTest` |

### 场景 4：TraceRecorder 创建 Trace 外壳

| 项目 | 内容 |
|------|------|
| 前置条件 | `enterprise.kb.trace.enabled=true`，采样命中，数据库可用 |
| 核心代码位置 | `kb-search/src/main/java/com/enterprise/kb/search/service/TraceRecorder.java`；`kb-search/src/main/java/com/enterprise/kb/search/service/impl/TraceRecorderImpl.java`；`kb-search/src/main/resources/mapper/AgentTraceMapper.xml` |
| 操作步骤 | 调用 `TraceRecorder.startTrace(...)`，传入 traceType、sessionId、spaceId、userId、inputText、rawInputJson |
| 期望结果 | 返回非空 `traceId` |
| 期望结果 | `agent_traces` 中新增一条记录 |
| 期望结果 | 记录状态为 `RUNNING` |
| 期望结果 | `started_at`、`created_at` 有值 |
| 期望结果 | `raw_input_json` 写入 JSONB，敏感字段已遮挡 |
| 当前状态 | 服务已实现，待数据库集成验收 |

### 场景 5：TraceRecorder 记录 Step

| 项目 | 内容 |
|------|------|
| 前置条件 | 已存在一条 `agent_traces` 记录 |
| 核心代码位置 | `kb-search/src/main/java/com/enterprise/kb/search/service/impl/TraceRecorderImpl.java`；`kb-search/src/main/java/com/enterprise/kb/search/mapper/AgentTraceStepMapper.java`；`kb-search/src/main/resources/mapper/AgentTraceStepMapper.xml` |
| 操作步骤 | 调用 `TraceRecorder.recordStep(traceId, stepRequest)` |
| 期望结果 | `agent_trace_steps` 中新增一条记录 |
| 期望结果 | `trace_id` 指向对应 Trace |
| 期望结果 | `step_index`、`step_type`、`status`、`input_json`、`output_json` 正确保存 |
| 期望结果 | 普通 step 走异步 best-effort 写入，不阻塞主流程 |
| 当前状态 | 服务已实现，待数据库集成验收 |

### 场景 6：HITL 中断 Step 同步写入

| 项目 | 内容 |
|------|------|
| 前置条件 | 已存在一条 `agent_traces` 记录 |
| 核心代码位置 | `kb-search/src/main/java/com/enterprise/kb/search/service/impl/TraceRecorderImpl.java` 的 `recordHitlInterrupt(...)`；`kb-search/src/main/resources/mapper/AgentTraceStepMapper.xml` |
| 操作步骤 | 调用 `TraceRecorder.recordHitlInterrupt(traceId, stepRequest)` |
| 期望结果 | `agent_trace_steps` 立即写入一条 `HITL_INTERRUPT` 或等价类型记录 |
| 期望结果 | 记录包含 `tool_call_id`、`tool_name`、工具入参和业务对象引用 |
| 期望结果 | 写入不依赖异步 executor 排队 |
| 当前状态 | 服务已实现，待业务接入和数据库集成验收 |

### 场景 7：Trace 成功完成

| 项目 | 内容 |
|------|------|
| 前置条件 | 已存在状态为 `RUNNING` 的 Trace |
| 核心代码位置 | `kb-search/src/main/java/com/enterprise/kb/search/service/impl/TraceRecorderImpl.java` 的 `completeTrace(...)`；`kb-search/src/main/resources/mapper/AgentTraceMapper.xml` 的 `complete` |
| 操作步骤 | 调用 `TraceRecorder.completeTrace(traceId, completeRequest)` |
| 期望结果 | `agent_traces.status` 更新为 `SUCCEEDED` |
| 期望结果 | `output_text`、`raw_output_json`、`duration_ms`、`tokens_used` 正确写入 |
| 期望结果 | `completed_at` 有值 |
| 当前状态 | 服务已实现，待数据库集成验收 |

### 场景 8：Trace 异常失败

| 项目 | 内容 |
|------|------|
| 前置条件 | 已存在状态为 `RUNNING` 的 Trace |
| 核心代码位置 | `kb-search/src/main/java/com/enterprise/kb/search/service/impl/TraceRecorderImpl.java` 的 `failTrace(...)`；`kb-search/src/main/resources/mapper/AgentTraceMapper.xml` 的 `fail` |
| 操作步骤 | 调用 `TraceRecorder.failTrace(traceId, error, durationMs)` |
| 期望结果 | `agent_traces.status` 更新为 `FAILED` |
| 期望结果 | `error_type` 记录异常类名 |
| 期望结果 | `error_message` 记录异常摘要 |
| 期望结果 | `duration_ms`、`completed_at` 正确写入 |
| 当前状态 | 服务已实现，待数据库集成验收 |

### 场景 9：Payload 超过上限时截断

| 项目 | 内容 |
|------|------|
| 前置条件 | `enterprise.kb.trace.max-payload-bytes` 设置为较小值 |
| 核心代码位置 | `kb-search/src/main/java/com/enterprise/kb/search/service/impl/TraceRecorderImpl.java` 的 `preparePayload(...)`；`kb-search/src/main/java/com/enterprise/kb/search/config/TraceProperties.java` |
| 操作步骤 | 记录一个超过上限的 raw JSON payload |
| 期望结果 | 保存的 payload 为包含 `truncated=true` 和 `preview` 的 JSON |
| 期望结果 | 对应记录 `payload_truncated=true` |
| 期望结果 | 主流程不抛出异常 |
| 当前状态 | 逻辑已实现，待单元/集成补充验收 |

### 场景 10：Trace 关闭或采样未命中时不写入

| 项目 | 内容 |
|------|------|
| 前置条件 | `enterprise.kb.trace.enabled=false` 或 `sample-rate=0` |
| 核心代码位置 | `kb-search/src/main/java/com/enterprise/kb/search/service/impl/TraceRecorderImpl.java` 的 `shouldRecord(...)`；`kb-search/src/main/java/com/enterprise/kb/search/config/TraceProperties.java` |
| 操作步骤 | 调用 `TraceRecorder.startTrace(...)` |
| 期望结果 | 返回 `Optional.empty()` |
| 期望结果 | 不新增 `agent_traces` 记录 |
| 期望结果 | 后续 record/complete/fail 调用传入空 traceId 时安全跳过 |
| 当前状态 | 逻辑已实现，待单元/集成补充验收 |

## Phase 1 验收场景：RAG Trace

### 场景 11：普通 RAG 问答生成 Trace

| 项目 | 内容 |
|------|------|
| 前置条件 | `QnAServiceImpl` 已接入 `TraceRecorder`，Trace 开关开启 |
| 核心代码位置 | `kb-search/src/main/java/com/enterprise/kb/search/service/impl/QnAServiceImpl.java` 的 `ask(...)`、`askWithTrace(...)`；`kb-search/src/main/java/com/enterprise/kb/search/service/impl/TraceRecorderImpl.java`；`kb-search/src/main/resources/mapper/AgentTraceMapper.xml`；`kb-search/src/main/resources/mapper/AgentTraceStepMapper.xml` |
| 操作步骤 | 调用普通 RAG 问答接口 |
| 期望结果 | `agent_traces` 新增一条 `STANDARD_QA` 或等价类型 Trace |
| 期望结果 | Trace 中记录用户问题、模型提供商、最终答案、token usage |
| 期望结果 | `agent_trace_steps` 记录 query rewrite、HyDE、retrieval、rerank、model call |
| 当前状态 | 代码已实现，待本地数据库接口验收 |

### 场景 12：普通 RAG 记录检索与 Rerank 明细

| 项目 | 内容 |
|------|------|
| 前置条件 | 普通 RAG 问答执行成功 |
| 核心代码位置 | `kb-search/src/main/java/com/enterprise/kb/search/service/impl/QnAServiceImpl.java` 的 `QUERY_REWRITE`、`HYDE`、`RETRIEVAL`、`RERANK` step 记录；`kb-search/src/main/java/com/enterprise/kb/search/service/HybridSearchService.java`；`kb-search/src/main/java/com/enterprise/kb/search/service/RerankService.java` |
| 操作步骤 | 查看对应 trace 详情 |
| 期望结果 | 能看到 hybrid search candidates |
| 期望结果 | 能看到 rerank topK |
| 期望结果 | 能看到最终 citations |
| 当前状态 | 代码已实现，待本地数据库接口验收 |

### 场景 13：Agentic RAG 工具调用生成 Step

| 项目 | 内容 |
|------|------|
| 前置条件 | `AgenticQnAServiceImpl` 已接入 `TraceRecorder` |
| 核心代码位置 | `kb-search/src/main/java/com/enterprise/kb/search/service/impl/AgenticQnAServiceImpl.java` 的 `ask(...)`、`buildSearchTool(...)`、`executeSearch(...)`、`recordSearchStep(...)` |
| 操作步骤 | 询问一个需要调用 `searchKnowledgeBase` 的问题 |
| 期望结果 | `agent_traces` 新增一条 `AGENTIC_QA` Trace |
| 期望结果 | 每次 `searchKnowledgeBase` 调用都有 `TOOL_CALL` step |
| 期望结果 | step 中包含 query、候选结果、rerank 结果、耗时 |
| 期望结果 | 最终 Trace 包含答案和 citations |
| 当前状态 | 代码已实现，待本地数据库接口验收 |

### 场景 14：Trace 写入失败不影响问答响应

| 项目 | 内容 |
|------|------|
| 前置条件 | 模拟 Trace Mapper 抛异常 |
| 核心代码位置 | `kb-search/src/main/java/com/enterprise/kb/search/service/impl/TraceRecorderImpl.java` 的 try/catch 保护；`kb-search/src/main/java/com/enterprise/kb/search/service/impl/QnAServiceImpl.java` 的 `ask(...)`；`kb-search/src/main/java/com/enterprise/kb/search/service/impl/AgenticQnAServiceImpl.java` 的 `ask(...)`、`executeSearch(...)` |
| 操作步骤 | 调用普通 RAG 或 Agentic RAG |
| 期望结果 | 用户仍收到正常问答响应 |
| 期望结果 | 应用日志记录 WARN |
| 期望结果 | 不向外暴露 trace 写入异常 |
| 当前状态 | 代码已实现，待异常注入集成验收 |

## Phase 2 待实现后验收场景：客服/售后/投诉工具 Trace

### 场景 15：客服助手路由过程生成 Trace Step

| 项目 | 内容 |
|------|------|
| 前置条件 | Phase 2 已接入 `CustomerAssistantServiceImpl.routedChat()` |
| 核心代码位置 | 待接入：`kb-search/src/main/java/com/enterprise/kb/search/service/impl/CustomerAssistantServiceImpl.java` 的 `routedChat(...)`、`shadowRoute(...)`、`dispatch(...)` |
| 操作步骤 | 用户发起客服会话 |
| 期望结果 | Trace 中记录 guard step |
| 期望结果 | Trace 中记录 router step，包含 domain、secondary、evidence |
| 期望结果 | Trace 中记录最终分派到的 domain handler |
| 当前状态 | 待实现 |

### 场景 16：售后资格检查工具调用可复现

| 项目 | 内容 |
|------|------|
| 前置条件 | Phase 2 已接入 `AfterSalesDomainHandler` |
| 核心代码位置 | 待接入：`kb-search/src/main/java/com/enterprise/kb/search/service/impl/AfterSalesDomainHandler.java` 的 `buildAgent(...)`、`checkAfterSalesEligibility(...)` |
| 操作步骤 | 用户提供订单号并触发 `checkAfterSalesEligibility` |
| 期望结果 | Trace step 中记录工具名 `checkAfterSalesEligibility` |
| 期望结果 | Trace step 中记录订单号入参 |
| 期望结果 | Trace step 中记录工具返回的资格判断结果 |
| 当前状态 | 待实现 |

### 场景 17：售后 HITL 中断可追踪

| 项目 | 内容 |
|------|------|
| 前置条件 | 用户触发 `submitAfterSalesReview`，HITL Hook 拦截 |
| 核心代码位置 | 待接入：`kb-search/src/main/java/com/enterprise/kb/search/service/impl/AfterSalesDomainHandler.java` 的 `processHitlInterrupt(...)`；`kb-search/src/main/java/com/enterprise/kb/search/service/impl/CustomerAssistantServiceImpl.java` 的 HITL 兼容路径 |
| 操作步骤 | 查看对应 trace |
| 期望结果 | Trace 中有 `HITL_INTERRUPT` step |
| 期望结果 | step 中包含 `tool_call_id`、`tool_call_name`、工具参数 |
| 期望结果 | step 关联 `REVIEW_REQUEST` 业务对象 |
| 当前状态 | 待实现 |

### 场景 18：投诉升级工具调用可追踪

| 项目 | 内容 |
|------|------|
| 前置条件 | Phase 2 已接入 `ComplaintDomainHandler` |
| 核心代码位置 | 待接入：`kb-search/src/main/java/com/enterprise/kb/search/service/impl/ComplaintDomainHandler.java` 的 `buildAgent(...)`、`escalateComplaint(...)` |
| 操作步骤 | 用户提供投诉内容和订单号，触发 `escalateComplaint` |
| 期望结果 | Trace step 中记录工具名 `escalateComplaint` |
| 期望结果 | Trace step 中记录 orderId、description |
| 期望结果 | Trace step 关联 `COMPLAINT` 或 `COMPLAINT_PLAN` 业务对象 |
| 当前状态 | 待实现 |

## Phase 3+ 待实现后验收场景：后台、Replay、Eval

### 场景 19：管理员查询 Trace 列表

| 项目 | 内容 |
|------|------|
| 前置条件 | Phase 3 API 已实现，用户为系统管理员或空间管理员 |
| 核心代码位置 | 已具备查询服务：`AgentTraceServiceImpl`；待新增：`AgentTraceController` / `/api/v1/admin/traces` 管理接口 |
| 操作步骤 | 调用 `GET /api/v1/admin/traces` |
| 期望结果 | 返回符合权限范围的 Trace 列表 |
| 期望结果 | 支持按时间、类型、sessionId、spaceId、status、tool、error 过滤 |
| 期望结果 | 非管理员访问被拒绝 |
| 当前状态 | 待实现 |

### 场景 20：导出 Replay JSON

| 项目 | 内容 |
|------|------|
| 前置条件 | Phase 3 API 已实现，存在完整 trace |
| 核心代码位置 | 待新增：`TraceReplayPackage` DTO、`AgentTraceService` replay 组装方法、`AgentTraceController` 的 `/replay` 接口 |
| 操作步骤 | 调用 `GET /api/v1/admin/traces/{traceId}/replay` |
| 期望结果 | 返回 replay JSON |
| 期望结果 | JSON 包含 input、history、systemPrompt、tools、steps、finalOutput |
| 期望结果 | 副作用工具只包含历史输出，不触发真实业务动作 |
| 当前状态 | 待实现 |

### 场景 21：从 Trace 生成 Eval Case 草稿

| 项目 | 内容 |
|------|------|
| 前置条件 | Phase 4 已实现 eval case 管理 |
| 核心代码位置 | 已具备基础服务：`EvalCaseServiceImpl`、`EvalCaseMapper.xml`；待新增：从 trace 生成草稿的 `AgentTraceService`/`EvalCaseService` 方法和 Controller 接口 |
| 操作步骤 | 调用 `POST /api/v1/admin/traces/{traceId}/eval-case` |
| 期望结果 | 新增一条 `eval_cases` 草稿 |
| 期望结果 | 草稿包含输入、历史、mock tool outputs |
| 期望结果 | 真值字段允许后续人工补标 |
| 当前状态 | 待实现 |

### 场景 22：JSONL 导入导出

| 项目 | 内容 |
|------|------|
| 前置条件 | Phase 4 已实现 JSONL 导入导出 |
| 核心代码位置 | 待新增：`EvalCaseService` JSONL import/export 方法；`EvalCaseController` 的 `/import-jsonl`、`/export-jsonl` 接口 |
| 操作步骤 | 导出 eval cases 为 JSONL，再导入 |
| 期望结果 | round-trip 后数据不丢失 |
| 期望结果 | 无效 JSONL 行返回明确错误行号和原因 |
| 当前状态 | 待实现 |

### 场景 23：三类评估门禁

| 项目 | 内容 |
|------|------|
| 前置条件 | Phase 5 已实现 Eval Replay Harness |
| 核心代码位置 | 已具备基础表/Mapper：`EvalRunMapper.xml`、`EvalRunResultMapper.xml`；待新增：`EvalReplayService`、评估断言实现和 eval run Controller |
| 操作步骤 | 运行 smoke dataset |
| 期望结果 | 路由/工具选择评估输出通过率 |
| 期望结果 | 检索质量评估输出 required doc/chunk 命中率 |
| 期望结果 | 答案质量评估输出断言通过率 |
| 期望结果 | 禁止副作用工具误调用数为 0 |
| 当前状态 | 待实现 |

### 场景 24：最小后台页面查看 Trace

| 项目 | 内容 |
|------|------|
| 前置条件 | Phase 6 页面已实现，用户为管理员 |
| 核心代码位置 | 待新增：`kb-app/src/main/resources/static/traces.html`；`kb-app/src/main/resources/static/evals.html`；后台 API：`AgentTraceController`、`EvalCaseController`、`EvalRunController` |
| 操作步骤 | 打开 `traces.html`，选择一条 Trace |
| 期望结果 | 页面展示 Summary / Prompt / Steps / Raw JSON / Replay |
| 期望结果 | 大 JSON 不影响基本查看 |
| 期望结果 | 非管理员无法获取页面数据 |
| 当前状态 | 待实现 |

## 当前测试记录

| 测试项 | 命令/方式 | 结果 |
|--------|-----------|------|
| 模块测试 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin:$PATH mvn test -pl kb-search -am` | 通过：105 tests，0 failures，0 errors，1 skipped |
| 配置默认值 | `TracePropertiesTest` | 通过 |
| 敏感字段遮挡 | `TraceRedactionServiceImplTest` | 通过 |

## 验收结论

Phase 0 可验收通过；Phase 1 RAG Trace 接入代码已完成，并通过 `kb-search` 模块测试。

下一轮应优先补充以下可执行验收：

1. 普通 RAG 生成完整 Trace。
2. Agentic RAG 的 `searchKnowledgeBase` 生成工具调用 Step。
3. Trace 写入失败不影响问答响应。
