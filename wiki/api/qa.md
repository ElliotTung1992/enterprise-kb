# QA 问答与会话 API

QA 接口分两组路径，**问答** 在 `/md-qa` 下，**会话管理** 在 `/qa/sessions` 下（路径前缀不同但底层共用 `qa_sessions` / `qa_messages` 两张表）。

权限要求：`VIEWER`（最低）

## 问答接口 — `/api/v1/spaces/{spaceId}/md-qa`

`MdQnAController`，针对 Markdown 结构感知 RAG 竖井。

### POST `/ask` — 标准 md QA（单次检索 → parent expansion → LLM）

同步阻塞，一次性返回完整答案。

**请求体**
```json
{
  "question": "如何申请退款？",
  "sessionId": "uuid（可选，传入延续多轮对话）",
  "modelProvider": "LLAMA_CPP",
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
        "documentTitle": "退款政策.md",
        "excerpt": "...",
        "section": "H2 — 退款条件",
        "score": 0.92,
        "assetUrl": "https://minio/...",
        "assetTitle": "示例配图"
      }
    ]
  }
}
```

### POST `/ask/agentic/stream` — Agentic 流式 md QA（ReactAgent 多轮）

`Content-Type: text/event-stream`。`MdAgenticQnAServiceImpl` 暴露双工具 `searchKnowledgeBase`（搜 child）+ `readFullSection`（按 parentId 回查整段），以 JSON 事件流返回 thinking / tool_call / tool_result / answer / citations / done。

## 会话管理接口 — `/api/v1/spaces/{spaceId}/qa/sessions`

`QnAController`，与问答端点路径前缀不同，但底层 `qa_sessions` / `qa_messages` 表共用。

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
      "updatedAt": "2026-05-30T12:00:00Z"
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

## 相关页面

- [[ai-rag/agentic-qa]] — Agentic md QA 实现细节
- [[ai-rag/session-memory]] — 会话记忆与多轮对话存储
- [[features/markdown-structure-rag]] — md 竖井检索链路
- [[decisions/adr-004-agentic-rag]] — Agentic 决策
