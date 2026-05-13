# qa_sessions & qa_messages

## qa_sessions 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | 首次问答时后端生成 |
| `space_id` | UUID | 所属知识空间 |
| `user_id` | UUID | 会话所有者 |
| `title` | VARCHAR(200) | 默认取首条问题截断50字符 |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | 每次问答刷新 |
| `deleted_at` | TIMESTAMPTZ | 软删除 |

## qa_messages 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | |
| `session_id` | UUID FK | ON DELETE CASCADE |
| `role` | VARCHAR(20) | `user` 或 `assistant` |
| `content` | TEXT | 消息正文 |
| `created_at` | TIMESTAMPTZ | |

## 双层存储

- **Redis**：`session:{sessionId}` → 消息列表（TTL 24h），供 LLM 上下文使用
- **PostgreSQL**：永久存储，供历史消息 API 使用

会话删除时，`QaChatSessionService` 同时清理 Redis key。

## 相关页面

- [[ai-rag/session-memory]] — 实现细节
- [[api/qa]] — 会话 API
