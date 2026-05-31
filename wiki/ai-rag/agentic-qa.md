# Agentic RAG 问答（md 竖井）

## 概念

Agentic QA 让 LLM 作为 **Agent** 自主决定检索策略：搜什么、搜几次、何时回查父段、何时停止。
适合复杂问题、多跳推理（multi-hop reasoning）场景。

## 实现

基于 **Spring AI Alibaba `ReactAgent`**（ReAct 框架），活跃实现为 `MdAgenticQnAServiceImpl`：

```
用户问题
    │
    ▼ MdAgenticQnAServiceImpl
    │
    └─ ReactAgent
        系统提示: 你是知识库助手，可调用 searchKnowledgeBase 搜检索 child，
                 必要时用 readFullSection 回查整段 parent
        工具:
          ├─ searchKnowledgeBase(query: String, topK: int)
          │      └─► MdHybridSearchService.search() → 向量 + 关键词 RRF 融合
          │         （可选 HyDE / 改写 / rerank）→ child 粒度结果
          └─ readFullSection(parentId: UUID)
                 └─► 按 parentId 回查整段 parent 原文（small-to-big）
        ReAct 循环:
            Thought → Action(调用工具) → Observation(检索结果) → ...
            直到 LLM 认为信息足够 → Final Answer
```

`MdHybridSearchService` 检索路径下细节见 [[ai-rag/hybrid-search]] · [[features/markdown-structure-rag]] · [[features/md-keyword-bm25]]。

## Token 预算

`AgenticTokenBudgetService` 控制 ReAct 循环的最大 token 消耗，防止无限循环。

- `compute(question, history)` → `Budget(historyTokensMax, retrievalSpace)`
- `compressHistory(...)` 历史超额时压缩

配置项：
- `enterprise.kb.ai.context-limit`（默认 204800）
- `enterprise.kb.ai.agentic.chunk-token-size`（默认 512）

Token 计数使用 **JTokkit** 库。

## 会话历史

`RedisChatMemory` 将对话历史持久化到 Redis：
- Key: `session:{sessionId}`
- TTL: 24 小时
- Agent 每轮都读取历史，实现多轮对话

## 与标准 md QA 的对比

| | 标准 md QA (`/md-qa/ask`) | Agentic md QA (`/md-qa/ask/agentic`) |
|--|--|--|
| 检索次数 | 1 次 | LLM 自主决定（多轮） |
| 父段回查 | 自动（topK child 折叠 parentId） | LLM 决定（`readFullSection` 工具） |
| 适用场景 | 简单问答 | 复杂 / 多跳推理 |
| 响应速度 | 快 | 较慢（多轮检索） |
| 引用来源 | 单次检索结果 | 所有轮次的 citations 聚合 |

> [!note] HITL 售后工具已独立
> Human-in-the-Loop 售后流程从未进入 md QA 链路，由独立的 `CustomerAssistantServiceImpl` 承载，使用独立的 `customer_sessions / customer_messages` 表。见 [[ai-rag/hitl-hook]] 和 [[decisions/adr-006-customer-assistant-separation]]。

## 相关文件

- `kb-search/src/main/java/.../service/impl/MdAgenticQnAServiceImpl.java`
- `kb-search/src/main/java/.../service/impl/MdQnAServiceImpl.java`
- `kb-search/src/main/java/.../ai/RedisChatMemory.java`
- `kb-search/src/main/java/.../service/AgenticTokenBudgetService.java`

## 相关页面

- [[api/qa]] — 端点列表
- [[ai-rag/session-memory]] — 会话记忆
- [[ai-rag/hybrid-search]] — 工具背后的检索链路
- [[features/markdown-structure-rag]] — small-to-big 父子索引
- [[decisions/adr-004-agentic-rag]] — Agentic 决策（含历史背景）
