# ADR-004: Agentic RAG（ReactAgent）

**状态**：已采用

## 背景

标准 RAG（单次检索）对多跳推理问题效果有限，需要 LLM 自主决定检索策略。

## 决策

引入 Spring AI Alibaba `ReactAgent`，将混合检索封装为 `searchKnowledgeBase` 工具，让 LLM 在 ReAct 循环中自主决定：搜什么、搜几次、何时停止。

## 实现要点

- Token 预算：`AgenticTokenBudgetService` 防止无限循环
- 会话历史：`RedisChatMemory`（Redis 持久化）
- Citations：每轮工具调用结果累积，最终一并返回

## 接口路径区分

- `/qa/ask` → Agentic（多轮）
- `/qa/ask/advanced` → 标准 RAG（单次）

## 权衡

| | Agentic | 标准 |
|--|--|--|
| 响应速度 | 较慢 | 快 |
| 复杂问题 | 好 | 一般 |
| Token 消耗 | 多 | 少 |
