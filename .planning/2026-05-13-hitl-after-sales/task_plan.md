# Task Plan: Human-in-the-Loop 售后审核功能

## Goal
在 AgenticQnAServiceImpl 中集成 Human-in-the-Loop 机制，实现 AI 引导用户提交售后申请、触发人工审核、审核员决策后通知用户的完整流程。

## Status: PHASES 1-6 COMPLETE — Phase 7 (集成测试) 待执行

---

## Phase 1: 数据库迁移 — review_requests 表
**Status**: complete

### 任务
- 新增 Liquibase 迁移文件 `018-add-review-requests.sql`
- 在 `db.changelog-master.xml` 中引用新文件

### 表结构
```sql
CREATE TABLE review_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL,          -- 对应 qa_sessions.id（也是 ReactAgent threadId）
    space_id UUID NOT NULL,
    user_id UUID,
    order_id VARCHAR(100),             -- 用户提交的订单号
    reason TEXT,                       -- 用户诉求（AI 提炼）
    conversation_snapshot JSONB,       -- 申请提交时的对话快照
    order_details JSONB,               -- simple-shop 查询到的订单详情
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING / APPROVED / REJECTED
    reviewer_id UUID,
    reviewer_comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at TIMESTAMPTZ
);
CREATE INDEX idx_review_requests_status ON review_requests(status);
CREATE INDEX idx_review_requests_session_id ON review_requests(session_id);
```

---

## Phase 2: Model + Mapper + Service
**Status**: complete

### 任务
- `ReviewRequest` model（`kb-search/model/ReviewRequest.java`）
- `ReviewRequestMapper` 接口 + XML（`kb-search`）
- `ReviewRequestService` 接口 + `ReviewRequestServiceImpl`
  - `createPending(sessionId, spaceId, userId, orderId, reason, conversationSnapshot, orderDetails)`
  - `approve(id, reviewerId, comment) → threadId`
  - `reject(id, reviewerId, comment) → threadId`
  - `listPending(spaceId) → List<ReviewRequest>`

---

## Phase 3: Redis Checkpoint Saver 配置
**Status**: complete

### 任务
- 在 `AiModelConfig` 或新建 `AgentConfig` 中配置 `RedisSaverConfig`
  - Spring AI Alibaba CheckpointSaver bean（基于已有 Redis 连接）
- 确认 `spring-ai-alibaba` 版本（1.1.2.1）中 CheckpointSaver 的 API

### 关键依赖
```xml
<!-- 检查 spring-ai-alibaba-graph-store-redis 是否需要单独引入 -->
```

---

## Phase 4: 工具实现 — checkAfterSalesEligibility
**Status**: complete

### 任务
- 新建 `AfterSalesTools.java`（或在 AgenticQnAServiceImpl 内部类）
- `checkAfterSalesEligibility(orderId: String) → String`
  - 查询 simple-shop API（或 mock）
  - 返回结构化文本：订单状态、金额、是否符合退款条件
  - 结果同时存入 `orderDetails` 供 review_requests 使用

---

## Phase 5: HumanInTheLoopHook 集成
**Status**: complete

### 任务
- 修改 `AgenticQnAServiceImpl.ask()`
- 添加 `HumanInTheLoopHook` 到 ReactAgent compileConfig
  - `afterModel` hook：拦截 `submitAfterSalesReview` 工具调用
  - 决策类型：仅 Approve / Reject（不支持 Edit）
- 工具 `submitAfterSalesReview(orderId, reason, orderDetailsJson) → String`
  - 写入 review_requests（PENDING）
  - 返回给用户："您的售后申请已提交，审核人员将在1个工作日内处理，结果将通知您"
- `threadId = sessionId.toString()`（复用现有 session 概念）

### 关键设计
```java
HumanInTheLoopHook hook = HumanInTheLoopHook.builder()
    .toolNames("submitAfterSalesReview")
    .checkpointSaver(checkpointSaver)
    .build();

ReactAgent reactAgent = ReactAgent.builder()
    ...
    .compileConfig(CompileConfig.builder()
        .recursionLimit(MAX_RECURSION_LIMIT)
        .hook(hook)
        .build())
    .build();
```

---

## Phase 6: 审核 API
**Status**: complete

### 任务
- 新建 `ReviewController`（`kb-search`）
  - `GET /api/v1/reviews/pending?spaceId=` — 审核员查看待处理列表（含诉求 + 对话快照 + 订单详情）
  - `POST /api/v1/reviews/{id}/approve` body: `{comment}`
  - `POST /api/v1/reviews/{id}/reject` body: `{comment}`
- approve/reject 后调用 `reactAgent.addHumanFeedback(threadId, decision)`
- 写入 `qa_messages`（role=assistant, content="您的售后申请已通过/已拒绝..."）

### 权限
- 审核员需要 EDITOR 或 OWNER 权限（`@PreAuthorize`）

---

## Phase 7: 集成测试 & 联调
**Status**: pending

### 任务
- 本地 mock simple-shop API（返回固定订单数据）
- 端到端测试完整流程：
  1. 用户说"退款"
  2. AI 引导输入订单号
  3. 检查资格 → 触发 submitAfterSalesReview
  4. HumanInTheLoopHook 拦截，Agent 暂停
  5. 审核员 GET /reviews/pending 看到申请
  6. 审核员 POST approve → Agent 恢复 → qa_messages 写入结果
  7. 用户重新打开会话看到审核结果

---

## Errors Encountered
| Error | Attempt | Resolution |
|-------|---------|------------|
| 事务顺序错误：先 approve 再 resumeWithFeedback | 1 | 反转顺序：先 LLM 再 DB，失败时 DB 不更新（见 ADR-005） |
| `@Transactional` 不生效（私有方法） | 1 | 注入 `TransactionTemplate`，显式调用 `executeWithoutResult()` |
| 审核中心无数据（前端仅取 PENDING 后客户端过滤） | 1 | 新增 `findByStatus` 动态 SQL + 服务端 Tab 过滤 |
| ReviewController 事务顺序同样错误 | 1 | /simplify 扫出后同步修正 |
| 首页空白（`requireAuth()` 在 api.js 不在 auth.js） | 1 | 补充 `<script src="/assets/js/api.js"></script>` |

---

## Decisions Log
| 决策点 | 结论 |
|--------|------|
| HITL 触发时机 | afterModel 拦截 submitAfterSalesReview 工具调用 |
| 异步/同步 | 异步：立即返回"审核中"，结果后续写入 qa_messages |
| threadId 映射 | sessionId == threadId（复用现有 session 概念） |
| Checkpoint 存储 | Redis（与 chat memory 共用同一 Redis 实例） |
| 审核员信息展示 | 诉求 + 对话快照 + 订单详情（JSONB 存 review_requests） |
| 结果通知方式 | 写入 qa_messages，用户下次打开会话时看到 |
| 审核 API 归属 | enterprise-kb 提供（含管理后台） |
| `@Transactional` 私有方法 | 使用 `TransactionTemplate.executeWithoutResult()` 替代 |
| 前端分离方案 | 门户首页两张入口卡片，商城页面不再出现在 KB 侧边栏 |
| 商城前端文件命名 | `after-sales.html` → `customer.html`（更语义化） |
| 规划文件位置 | `.planning/2026-05-13-hitl-after-sales/`（隔离于项目根目录）|
