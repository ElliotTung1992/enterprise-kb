# Findings: Human-in-the-Loop 售后审核

## Spring AI Alibaba HumanInTheLoopHook

### 文档来源
https://java2ai.com/docs/frameworks/agent-framework/advanced/human-in-the-loop

### HumanInTheLoopHook 机制
- 类型：`afterModel` hook，在模型输出工具调用请求后、实际执行工具前拦截
- 触发：LLM 决定调用被监控的工具名时暂停执行
- 三种决策：
  - ✅ Approve（批准）：工具按原参数执行
  - ✏️ Edit（修改）：修改工具参数后执行
  - ❌ Reject（拒绝）：工具不执行，向 LLM 注入拒绝原因
- 恢复：`reactAgent.addHumanFeedback(threadId, feedback)` 恢复被暂停的 Agent

### Checkpoint Saver
- 用途：Agent 暂停时持久化图状态（消息列表、中间步骤）
- 支持 Redis / PostgreSQL 两种后端
- 配置 bean：`CheckpointSaver`（Spring AI Alibaba graph 模块）
- 依赖：需确认是否需要额外 `spring-ai-alibaba-graph-store-redis` 依赖

### threadId
- ReAct 图中每个独立执行上下文的唯一 ID
- 设计决策：`threadId = sessionId.toString()`（复用现有 QA 会话概念）

### 当前项目版本
- `spring-ai-alibaba` version: 1.1.2.1
- 需要验证该版本 HumanInTheLoopHook API 的确切方法签名

---

## 业务流程设计

### 完整流程
```
用户: "我要退款"
→ AgenticQnAServiceImpl (ReactAgent, threadId=sessionId)
→ AI: "请提供您的订单号"
→ 用户提供订单号
→ Tool: checkAfterSalesEligibility(orderId)
  → 查询 simple-shop API 获取订单详情
  → 判断是否符合退款条件
→ 符合条件 → Tool: submitAfterSalesReview(orderId, reason, orderDetails)
  → HumanInTheLoopHook 拦截（afterModel 阶段）
  → 写 review_requests（status=PENDING）含对话快照
  → Checkpoint 存 Redis（保存 Agent 图状态）
  → Agent 返回: "您的申请已提交，审核中"

审核员侧:
→ GET /api/v1/reviews/pending 查看申请（诉求+对话+订单）
→ POST /api/v1/reviews/{id}/approve (or /reject)
  → ReviewRequestService.approve() → 写 decided_at, status=APPROVED
  → reactAgent.addHumanFeedback(threadId=sessionId, "APPROVED")
  → Agent 恢复执行
  → Agent 生成最终回复
  → 写 qa_messages: "您的售后申请已通过，退款将在3-5个工作日内到账"

用户侧:
→ 下次打开会话 → GET /sessions/{id}/messages
→ 看到审核结果消息
```

### 数据库表关系
- `review_requests.session_id` → `qa_sessions.id`（1:N，一个会话可有多次申请）
- `review_requests.session_id` = ReactAgent `threadId`（checkpoint 查找键）

---

## 现有代码结构（关键文件）

### AgenticQnAServiceImpl
- 路径: `kb-search/src/main/java/com/enterprise/kb/search/service/impl/AgenticQnAServiceImpl.java`
- 当前工具: `searchKnowledgeBase`（FunctionToolCallback）
- 需新增: `checkAfterSalesEligibility`, `submitAfterSalesReview`
- 需新增: `HumanInTheLoopHook` 配置, Checkpoint Saver 注入

### 现有 Redis 配置
- `RedisChatMemory` 已使用 Redis 存储对话历史
- Checkpoint Saver 可复用同一 RedisConnectionFactory

### Liquibase 现状
- 最新迁移: `017-drop-mcp-api-keys.sql`
- 新文件命名: `018-add-review-requests.sql`
- 主配置: `kb-app/src/main/resources/db/changelog/db.changelog-master.xml`

---

## 注意事项 / 风险

1. **HumanInTheLoopHook API 兼容性**: v1.1.2.1 的具体 API 需要查看源码确认，文档示例可能基于更新版本
2. **Checkpoint 恢复**: Agent 重启后（如服务重部署），Redis 中的 checkpoint 仍有效，需要能重新 bind ReactAgent
3. **并发安全**: 同一 sessionId 不应同时有多个 pending review，需要业务层约束
4. **simple-shop API 不可用**: checkAfterSalesEligibility 需要 fallback（mock 数据 / 友好错误提示）
5. **长时间 pending**: Redis checkpoint TTL 需要与业务 SLA 对齐（建议 7 天）
