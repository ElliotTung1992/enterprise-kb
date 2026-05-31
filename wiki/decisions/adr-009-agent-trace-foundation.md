---
created: 2026-05-18
tags: [adr, tombstone, deprecated, trace, observability]
---

# ADR-009：自研 Agent Trace（已废止）

**状态**：**已废止**——随迁移 032 整体下线（2026-05-28）

## 概要

原 ADR-009 提议建一套自研 Agent Trace 框架：`agent_traces` / `agent_trace_steps` 表 + `TraceRecorder` + `TraceFacade` + ChatClient advisor + agent 拦截器 + AOP，用于追踪 Agentic RAG / 客服助手 / 售后 HITL / 投诉升级的工具调用与多步循环。

## 废止原因

落库形态的 trace 维护成本高，且在跨线程 / reactor / virtual thread 场景上下文传播脆弱；线上请求路径与离线评估之间的复用度低。下线后通过迁移 032 删除 `agent_traces` / `agent_trace_steps` 两张表，并清理相关 advisor / 拦截器 / AOP 代码。

## 接续

- **在线 LLM tracing**：[[decisions/adr-015-langfuse-tracing]] —— Micrometer Observation → OTLP → 自部署 LangFuse；trace 只走 OTLP，不进业务库。
- **离线评估**：[[decisions/adr-014-ragas-integration]] —— 基于 `eval_*` 表 + Ragas LLM-as-judge。

## 关联

- 实施期：[.planning/2026-05-18-agent-trace-eval/](#)（含 acceptance_report）
- 接续 ADR：[[decisions/adr-014-ragas-integration]] · [[decisions/adr-015-langfuse-tracing]]
- 关联废止：[[decisions/adr-010-agent-trace-eval]]
