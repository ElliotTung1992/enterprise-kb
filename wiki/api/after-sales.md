---
created: 2026-05-13
tags: [api, after-sales, customer-assistant, hitl]
---

# API：售后客服助手 `/api/v1/after-sales`

控制器：`CustomerAssistantController`，路由前缀 `/api/v1/after-sales`。

所有接口需要有效 JWT Token（标准 Bearer 认证）。审核员接口无额外空间权限要求。

## 用户对话接口

### 发送消息

```
POST /api/v1/after-sales/ask
```

**请求体：**
```json
{
  "sessionId": "uuid（可选，不传则创建新会话）",
  "message": "我要申请退款"
}
```

**响应：**
```json
{
  "success": true,
  "data": {
    "sessionId": "uuid",
    "reply": "AI 回复内容",
    "interrupted": false
  }
}
```

> [!note]
> 若触发 HITL 中断（`interrupted=true`），`reply` 为"您的申请已提交，审核中"，Agent 暂停等待审核员决策。

---

### 会话管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/sessions` | 当前用户会话列表（按最后活跃时间倒序，含消息数） |
| `GET` | `/sessions/{sessionId}/messages` | 指定会话的历史消息（时间升序） |
| `DELETE` | `/sessions/{sessionId}` | 软删除会话 |

---

## 审核员接口

### 查询审核申请列表

```
GET /api/v1/after-sales/reviews?status=PENDING
```

`status` 可选值：`PENDING` / `APPROVED` / `REJECTED`，不传返回全部。

**响应**：`ReviewRequest` 列表，含订单号、诉求、对话快照、订单详情 JSONB。

---

### 批准申请

```
POST /api/v1/after-sales/reviews/{id}/approve
Content-Type: application/json

{ "comment": "审核通过，退款受理" }
```

执行顺序（事务安全）：
1. `reviewRequestService.findById(id)` — 加载申请
2. `customerAssistantService.resumeWithFeedback(sessionId, APPROVED)` — 恢复 Agent（LLM 调用）
3. `reviewRequestService.approve(id, reviewerId, comment)` — 写 DB（仅在 LLM 成功后）

**响应**：Agent 恢复后生成的最终回复（`CustomerAssistantResponse`）。

---

### 拒绝申请

```
POST /api/v1/after-sales/reviews/{id}/reject
Content-Type: application/json

{ "comment": "不符合退款条件" }
```

同上顺序，`FeedbackResult.REJECTED` 注入 Agent。

---

## 前端页面

| 页面 | 路径 | 用途 |
|------|------|------|
| 首页门户 | `/index.html` | 入口，导航到 KB 或商城客服 |
| 客户助手 | `/customer.html` | 用户对话界面 |
| 审核中心 | `/reviews.html` | 审核员视图，含 Tab 过滤 |

## 相关页面

- [[features/hitl-after-sales]] — 业务流程
- [[ai-rag/hitl-hook]] — HITL 机制
- [[database/entities/after-sales-tables]] — 数据表
