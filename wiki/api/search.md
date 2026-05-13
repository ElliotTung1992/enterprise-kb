# 搜索 API

基础路径：`/api/v1/spaces/{spaceId}/search`
权限要求：`VIEWER`

## POST `/` — 执行搜索

**请求体**
```json
{
  "query": "退款政策",
  "mode": "HYBRID",
  "topK": 10,
  "modelProvider": "DASHSCOPE"
}
```

`mode` 枚举：`SEMANTIC`（语义） / `KEYWORD`（关键词） / `HYBRID`（混合，推荐）

**响应**
```json
{
  "data": {
    "hits": [
      {
        "chunkId": "uuid",
        "documentId": "uuid",
        "documentName": "退款政策.pdf",
        "content": "...",
        "score": 0.89,
        "highlightedContent": "...<em>退款</em>..."
      }
    ],
    "total": 10,
    "searchMode": "HYBRID",
    "latencyMs": 234
  }
}
```

## 搜索增强特性

| 特性 | 说明 | 默认 |
|------|------|------|
| HyDE | 先生成假设答案再检索，提升语义召回 | 按需启用 |
| 查询改写 | LLM 扩展/改写查询词 | 按需启用 |
| Rerank | DashScope gte-rerank 精排 | QA 流程中默认启用 |
| RRF 融合 | Hybrid 模式固定使用，k=60 | 自动 |
