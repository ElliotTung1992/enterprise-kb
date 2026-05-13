---
created: 2026-05-13
tags: [database, after-sales, hitl, customer-assistant]
---

# 数据表：售后客服

迁移文件：018、019、020。表均在 `enterprise_kb` 数据库中。

## review_requests（迁移 018）

HITL 审核申请表，每条记录对应一次用户提交的售后申请。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | 审核申请 ID |
| `session_id` | UUID | 关联 `customer_sessions.id`，同时作为 ReactAgent `threadId` |
| `space_id` | UUID NULL | 可空（客户助手无知识空间，迁移 020 改为可空） |
| `user_id` | UUID | 申请用户 ID |
| `order_id` | VARCHAR(100) | 用户提交的订单号 |
| `reason` | TEXT | 用户诉求（AI 提炼） |
| `conversation_snapshot` | JSONB | 提交时的对话快照 |
| `order_details` | JSONB | simple-shop API 返回的订单详情 |
| `tool_call_id` | VARCHAR | HITL hook 拦截的工具调用 ID |
| `tool_call_name` | VARCHAR | 被拦截的工具名（`submitAfterSalesReview`） |
| `status` | VARCHAR(20) | `PENDING` / `APPROVED` / `REJECTED` |
| `reviewer_id` | UUID NULL | 审核员用户 ID |
| `reviewer_comment` | TEXT NULL | 审核意见 |
| `created_at` | TIMESTAMPTZ | 创建时间 |
| `updated_at` | TIMESTAMPTZ | 最后更新时间 |
| `decided_at` | TIMESTAMPTZ NULL | 审核决策时间 |

**索引：**
- `idx_review_requests_status` ON `(status)`
- `idx_review_requests_session_id` ON `(session_id)`

**状态枚举：** `ReviewStatus`（`kb-common/constants/ReviewStatus.java`）

---

## customer_sessions（迁移 019）

商城客服对话会话表，独立于 `qa_sessions`。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | 会话 ID（同时作为 ReactAgent threadId） |
| `user_id` | UUID | 会话所有者 |
| `title` | VARCHAR(200) | 默认取首条消息前 50 字 |
| `created_at` | TIMESTAMPTZ | 创建时间 |
| `updated_at` | TIMESTAMPTZ | 最后活跃时间 |
| `deleted_at` | TIMESTAMPTZ NULL | 软删除标记 |

---

## customer_messages（迁移 019）

商城客服消息表，独立于 `qa_messages`。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | 消息 ID |
| `session_id` | UUID FK | → `customer_sessions.id` ON DELETE CASCADE |
| `role` | VARCHAR(20) | `user` 或 `assistant` |
| `content` | TEXT | 消息正文 |
| `created_at` | TIMESTAMPTZ | 消息时间 |

---

## 表关系

```
customer_sessions ──(1:N)──► customer_messages
customer_sessions ──(1:N)──► review_requests (session_id)
review_requests.session_id == ReactAgent threadId (checkpoint key in Redis)
```

## 与 QA 会话表的对比

| 特征 | qa_sessions | customer_sessions |
|------|-------------|------------------|
| 绑定知识空间 | 是（space_id NOT NULL） | 否（无 space_id） |
| HITL 审核 | 无 | 通过 review_requests |
| 服务层 | QnAServiceImpl | CustomerAssistantServiceImpl |

## 相关页面

- [[features/hitl-after-sales]] — 业务流程
- [[database/schema-overview]] — 完整表清单
- [[database/entities/qa-sessions]] — 知识库会话对比
