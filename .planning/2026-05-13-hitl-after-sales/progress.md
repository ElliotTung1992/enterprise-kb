# Progress Log

## Session: 2026-05-13

### Context
从上一 session 延续，已完成 HITL 需求分析与设计（/grill-me），现在进入实现阶段。

### 设计确认完毕
- 触发时机：afterModel 拦截 `submitAfterSalesReview`
- 审核模式：异步（立即返回"审核中"，结果后续写入 qa_messages）
- threadId：sessionId == threadId
- Checkpoint：Redis
- 结果通知：写 qa_messages，用户下次打开会话查看
- 审核员 API 归属：enterprise-kb（含管理后台）

### 创建计划文件
- [x] task_plan.md
- [x] findings.md
- [x] progress.md

### Phase 1 — DB 迁移 ✅
- 新增 `018-add-review-requests.sql`（含 tool_call_id、tool_call_name 字段）
- 更新 `db.changelog-master.xml`

### Phase 2 — Model + Mapper + Service ✅
- `ReviewRequest` model
- `ReviewRequestMapper` 接口 + XML
- `ReviewRequestService` 接口 + `ReviewRequestServiceImpl`

### Phase 3 — Redis Checkpoint Saver 配置 ✅
- `AgentCheckpointConfig.java`：`RedissonClient` + `RedisSaver` bean
- `kb-search/pom.xml` 添加 `org.redisson:redisson:3.22.0`

### Phase 4 — 工具 DTO ✅
- `AfterSalesCheckInput` record
- `AfterSalesSubmitInput` record

### Phase 5 — HumanInTheLoopHook 集成 ✅
- 完整重写 `AgenticQnAServiceImpl`
- 新增 `AgenticQnAService.resumeWithFeedback()` 接口方法
- `HumanInTheLoopHook` 拦截 `submitAfterSalesReview`
- `SubGraphInterruptionException` 捕获 → 写 `review_requests` → 返回"审核中"
- `resumeWithFeedback()` 重建 ReactAgent + 注入 InterruptionMetadata

### Phase 6 — 审核 API ✅
- `ReviewController`：GET /pending, POST /{id}/approve, POST /{id}/reject
- approve/reject 先查申请（获取 toolCallId）再决策，再恢复 Agent

### 构建状态
- `mvn install -pl kb-app -am -DskipTests` ✅ BUILD SUCCESS

### 单元测试 ✅ (11 tests, 0 failures)
- `HybridSearchServiceImplTest` — 5 个测试，验证 RRF 融合算法行为
- `ReviewRequestServiceImplTest` — 6 个测试，验证 HITL 审核申请业务逻辑
- `kb-search/pom.xml` 添加 `spring-boot-starter-test`

### /simplify 修复 ✅ (11 tests, 0 failures)
- **ReviewStatus 枚举**：新增 `kb-common` 中的 `ReviewStatus` 枚举（PENDING/APPROVED/REJECTED），替代 stringly-typed 字符串
- **消除双重 DB 查询**：`approve()/reject()` 返回类型从 `UUID` 改为 `ReviewRequest`，`ReviewController` 不再额外调用 `findById`
- **null toolCallId 抛异常**：`buildFeedback()` 改为在 toolCallId 为 null 时抛 `KbException(UNPROCESSABLE_ENTITY)`
- **ResponseEntity 包装**：`ReviewController` 返回类型改为 `ResponseEntity<ApiResponse<T>>`，与 `QnAController` 保持一致
- **ObjectMapper 替代手写 JSON**：`extractJsonField()` 和 `serializeMessagesToJson()` 改用注入的 `ObjectMapper`，修复了多行 LLM 响应的 JSON 正确性 bug
- **移除 `rawHistory` 死代码**：`resumeWithFeedback()` 中不再调用无用的 `redisChatMemory.get()`
- **静态 budget 哨兵**：`RESUME_BUDGET` 常量替代 `tokenBudget.compute("", List.of())`
- **SearchAccumulator 内部类**：将 5 个检索状态变量封装为 `SearchAccumulator`，`buildSearchTool` 参数从 7 个减到 3 个
- **提取 `buildReactAgent()`**：`ask()` 和 `resumeWithFeedback()` 共享同一个 Agent 构建方法，消除重复
- **Redisson 连接池缩减**：`connectionPoolSize=4, connectionMinimumIdleSize=1`（原为 64/24）
- **移除冗余注释**：删除了描述代码行为的无意义内联注释

### 商城客户助手独立拆分 ✅
问题：HITL 售后工具与知识库问答混在 AgenticQnAServiceImpl 中。
解决：完全拆离，新建独立的商城客户助手模块。

新增文件：
- `019-add-customer-sessions.sql`：新表 customer_sessions / customer_messages
- `020-make-review-space-id-nullable.sql`：review_requests.space_id 改为可空（客户助手无空间）
- `CustomerSession.java` / `CustomerAssistantRequest.java` / `CustomerAssistantResponse.java`
- `CustomerSessionDto.java` / `CustomerMessageDto.java`
- `CustomerSessionMapper.java` + `CustomerSessionMapper.xml`
- `CustomerMessageMapper.java` + `CustomerMessageMapper.xml`
- `CustomerAssistantService.java` + `CustomerAssistantServiceImpl.java`
- `CustomerAssistantController.java`

修改文件：
- `AgenticQnAServiceImpl`：回归纯 Agentic RAG，移除所有售后工具、HITL hook、ReviewRequestService
- `AgenticQnAService`：移除 resumeWithFeedback 方法
- `ReviewController`：注入 CustomerAssistantService 替代 AgenticQnAService
- `ReviewRequestService` / `Impl` / `Mapper` / `XML`：新增 listAllPending() 方法

API 拓扑：
- `POST /api/v1/after-sales/ask` — 客户助手对话（含 HITL 中断）
- `GET  /api/v1/after-sales/sessions` — 用户会话列表
- `GET  /api/v1/after-sales/sessions/{id}/messages` — 会话历史
- `DELETE /api/v1/after-sales/sessions/{id}` — 删除会话
- `GET  /api/v1/after-sales/reviews/pending` — 审核员看所有待审核申请
- `POST /api/v1/after-sales/reviews/{id}/approve` — 批准（恢复 Agent）
- `POST /api/v1/after-sales/reviews/{id}/reject`  — 拒绝（恢复 Agent）

### 下一步
Phase 7: 集成测试 & 联调（需要启动完整基础设施）

---

## Session: 2026-05-13（续）

### 本轮修复 & 收尾

#### 前端文件重命名 ✅
- `after-sales.html` → `customer.html`
- 更新所有引用：
  - `index.html`：入口卡片 href
  - `customer.html`：自身导航激活链接
  - `reviews.html`：侧边栏客户助手链接

#### 规划文件迁移 ✅
- `task_plan.md / findings.md / progress.md` 从项目根目录移至 `.planning/2026-05-13-hitl-after-sales/`
- `.planning/.active_plan` 指向当前活跃计划

#### Wiki 入库 ✅
新建 6 个页面：
- `wiki/features/hitl-after-sales.md`
- `wiki/ai-rag/hitl-hook.md`
- `wiki/api/after-sales.md`
- `wiki/database/entities/after-sales-tables.md`
- `wiki/decisions/adr-005-hitl-transaction-ordering.md`
- `wiki/decisions/adr-006-customer-assistant-separation.md`

更新 5 个页面：Home.md / schema-overview.md / agentic-qa.md / hot.md / log.md

#### task_plan.md 更新 ✅
- 补充所有错误记录（事务顺序、私有方法事务、前端问题）
- 补充所有决策（TransactionTemplate、前端分离、文件命名）
- 状态更新：Phases 1-6 complete，Phase 7 pending
