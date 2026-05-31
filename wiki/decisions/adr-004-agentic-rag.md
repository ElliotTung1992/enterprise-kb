---
created: 2026-04-01
tags: [adr, agentic-rag, react-agent, spring-ai-alibaba]
---

# ADR-004: Agentic RAG（ReactAgent）

**状态**：已采用（原 `AgenticQnAServiceImpl` 随迁移 031 退役，当前活跃实现为 `MdAgenticQnAServiceImpl`，工具集替换为 `searchKnowledgeBase` + `readFullSection`）

## 背景

单次检索对多跳推理问题效果有限，需要 LLM 自主决定检索策略。

## 决策

引入 Spring AI Alibaba `ReactAgent`，将检索封装为工具暴露给 LLM，在 ReAct 循环中自主决定：搜什么、搜几次、何时停止、是否回查父段。

## 实现要点（当前 md 竖井）

- 双工具：`searchKnowledgeBase`（搜 child）+ `readFullSection`（按 parentId 回查整段）—— small-to-big 父子索引
- Token 预算：`AgenticTokenBudgetService` 防止无限循环（`Budget(historyTokensMax, retrievalSpace)` + `compressHistory`）
- 会话历史：`RedisChatMemory`（Redis 持久化，key=`session:{sessionId}`，TTL 24h）
- Citations：每轮工具调用结果累积，最终一并返回

## 接口路径

- `/api/v1/spaces/{spaceId}/md-qa/ask` → 标准 md QA（单次检索 → parent expansion → LLM）
- `/api/v1/spaces/{spaceId}/md-qa/ask/agentic` → Agentic（ReactAgent 多轮）
- `/api/v1/spaces/{spaceId}/md-qa/ask/stream` → 流式（SSE）

## 权衡

| | Agentic | 标准（单次） |
|--|--|--|
| 响应速度 | 较慢 | 快 |
| 复杂问题 | 好 | 一般 |
| Token 消耗 | 多 | 少 |

## 关联

- 接续：[[features/markdown-structure-rag]] · [[decisions/adr-012-markdown-structure-rag]]
- 评估：[[features/ragas-evaluation]] · [[decisions/adr-014-ragas-integration]]
- 在线 tracing：[[features/langfuse-tracing]] · [[decisions/adr-015-langfuse-tracing]]
