# 会话记忆 & 多轮对话

## 存储层次

| 层 | 存储 | 内容 | 生命周期 |
|----|------|------|---------|
| 上下文缓存 | Redis | 对话消息列表 (JSON) | TTL 24h，Key=`session:{sessionId}` |
| 持久化记录 | PostgreSQL | qa_sessions + qa_messages | 永久（软删除） |

## 会话创建时机

首次调用 `/qa/ask` 或 `/qa/ask/advanced` 时，`QaChatSessionService.saveExchange()` 自动：
1. 生成 `sessionId` (UUID)
2. 创建 `qa_sessions` 记录，`title` = 首条问题截断至 50 字符
3. 写入 `qa_messages`（role=user + role=assistant）

后续调用传入相同 `sessionId` 即可延续对话。

## Redis 键结构

```
session:{sessionId}  →  List<Message>
  [
    {"role": "user",      "content": "..."},
    {"role": "assistant", "content": "..."},
    ...
  ]
```

`RedisChatMemory` 类负责读写，供 `AgenticQnAServiceImpl` 和 `QnAServiceImpl` 使用。

## 持久化失败处理

`saveExchange()` 失败时：
- 仅记录 `WARN` 日志
- **不影响正常问答响应**（持久化是 best-effort）

## 会话管理 API

见 [[api/qa]] 中的会话管理接口：
- `GET /qa/sessions` — 会话列表
- `GET /qa/sessions/{id}/messages` — 历史消息
- `PATCH /qa/sessions/{id}/title` — 改标题
- `DELETE /qa/sessions/{id}` — 软删除 + 清 Redis
