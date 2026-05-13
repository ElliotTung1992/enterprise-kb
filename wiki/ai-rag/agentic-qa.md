# Agentic RAG 问答

## 概念

Agentic QA 让 LLM 作为 **Agent** 自主决定检索策略：搜什么、搜几次、何时停止。
适合复杂问题、多跳推理（multi-hop reasoning）场景。

## 实现

基于 **Spring AI Alibaba `ReactAgent`**（ReAct 框架）：

```
用户问题
    │
    ▼ AgenticQnAServiceImpl
    │
    └─ ReactAgent
        系统提示: 你是知识库助手，使用 searchKnowledgeBase 工具检索
        工具: searchKnowledgeBase(query: String, topK: int)
            └─ HybridSearchService.search() + RerankService.rerank()
               → 返回 citations 列表
        ReAct 循环:
            Thought → Action(调用工具) → Observation(检索结果) → ...
            直到 LLM 认为信息足够 → Final Answer
```

## Token 预算

`AgenticTokenBudgetService` 控制 ReAct 循环的最大 token 消耗，防止无限循环。

配置项：
- `enterprise.kb.ai.context-limit`（默认 204800）
- `enterprise.kb.ai.agentic.chunk-token-size`（默认 512）

Token 计数使用 **JTokkit** 库。

## 会话历史

`RedisChatMemory` 将对话历史持久化到 Redis：
- Key: `session:{sessionId}`
- TTL: 24 小时
- Agent 每轮都读取历史，实现多轮对话

## 与标准 QA 的对比

| | 标准 QA (`/qa/ask/advanced`) | Agentic QA (`/qa/ask`) |
|--|--|--|
| 检索次数 | 1次 | LLM 自主决定（多轮） |
| 适用场景 | 简单问答 | 复杂/多跳推理 |
| 响应速度 | 快 | 较慢（多轮检索） |
| 引用来源 | 单次检索结果 | 所有轮次的 citations 聚合 |

> [!note] HITL 售后工具已独立
> Human-in-the-Loop 售后流程已从 `AgenticQnAServiceImpl` 中彻底剥离，由独立的 `CustomerAssistantServiceImpl` 承载，使用独立的 `customer_sessions / customer_messages` 表。见 [[ai-rag/hitl-hook]] 和 [[decisions/adr-006-customer-assistant-separation]]。

## 相关文件

- `kb-search/src/main/java/.../service/impl/AgenticQnAServiceImpl.java`
- `kb-search/src/main/java/.../ai/RedisChatMemory.java`
- `kb-search/src/main/java/.../service/AgenticTokenBudgetService.java`
