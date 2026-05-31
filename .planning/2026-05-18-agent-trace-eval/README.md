# Agent Trace、离线复现包与评估回放（2026-05-18）

`agent_traces` / `agent_trace_steps` + `TraceRecorder` + `TraceFacade` + ChatClient advisor + agent 拦截器 + AOP 的自研 trace 框架；目标是让 Agentic RAG / 客服助手 / 售后 HITL / 投诉升级可追踪、可复现、可沉淀为评估门禁。

## 状态

**已退役**——2026-05-28 经迁移 032 全部下线，落库形态被废弃。在线可观测性改由 LangFuse via OTLP 接续（见 [2026-05-29-langfuse-tracing/](#)，对应 [[decisions/adr-015-langfuse-tracing]]）。离线评估迁移至 Ragas（[[features/ragas-evaluation]] / [[decisions/adr-014-ragas-integration]]）。

## 文件

- [task_plan.md](task_plan.md)
- [progress.md](progress.md)
- [findings.md](findings.md)
- [acceptance_report.md](acceptance_report.md)
- [agent-trace-eval-tables.puml](agent-trace-eval-tables.puml) / [agent-trace-eval-tables.png](agent-trace-eval-tables.png)

## 关联 wiki

- [[decisions/adr-015-langfuse-tracing]]（接续在线 tracing）
- [[decisions/adr-014-ragas-integration]]（接续离线评估）
