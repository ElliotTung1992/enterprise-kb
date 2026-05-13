# QA 问答 API

基础路径：`/api/v1/spaces/{spaceId}/qa`
权限要求：`VIEWER`（最低）

## 问答接口

### POST `/ask/advanced` — 标准 RAG 问答

同步阻塞，一次性返回完整答案。

**请求体**
```json
{
  "question": "如何申请退款？",
  "sessionId": "uuid（可选，传入延续多轮对话）",
  "modelProvider": "MINIMAX",
  "topK": 5
}
```

**响应**
```json
{
  "success": true,
  "data": {
    "answer": "...",
    "sessionId": "uuid",
    "citations": [
      {
        "chunkId": "uuid",
        "documentId": "uuid",
        "documentName": "退款政策.pdf",
        "content": "...",
        "score": 0.92
      }
    ]
  }
}
```

### POST `/ask` — Agentic RAG 问答

LLM 自主多轮检索，适合复杂问题。请求/响应结构同上。

### GET `/ask/stream` — 流式问答 (SSE)

`Content-Type: text/event-stream`，逐 token 推送。

---

## 会话管理接口

### GET `/sessions` — 会话列表

返回当前用户在该空间下的会话列表，按 `updated_at` 倒序，含每个会话的消息数。

**响应**
```json
{
  "data": [
    {
      "id": "uuid",
      "title": "如何申请退款",
      "messageCount": 6,
      "updatedAt": "2024-01-01T12:00:00Z"
    }
  ]
}
```

### GET `/sessions/{sessionId}/messages` — 历史消息

仅会话所有者可访问。

**响应**
```json
{
  "data": [
    {"role": "user", "content": "...", "createdAt": "..."},
    {"role": "assistant", "content": "...", "createdAt": "..."}
  ]
}
```

### PATCH `/sessions/{sessionId}/title` — 修改标题

```json
{"title": "新标题"}
```

### DELETE `/sessions/{sessionId}` — 删除会话

软删除会话 + 清空对应 Redis 历史。
