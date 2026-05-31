---
created: 2026-05-18
tags: [adr, tombstone, deprecated, evaluation, trace, replay]
---

# ADR-010：自研 Trace 评估回放（已废止）

**状态**：**已废止**——随迁移 032 与 [[decisions/adr-009-agent-trace-foundation]] 一并退役（2026-05-28）

## 概要

原 ADR-010 在 [[decisions/adr-009-agent-trace-foundation]] 之上叠加「离线复现包 + 评估回放」能力：将自研 trace 沉淀为评估门禁，使工具调用可复现、可回放、可作为质量基线。

## 废止原因

自研 trace 落库形态本身被废（见 ADR-009），其上的评估回放无所依附。**评估职能改由独立的 `eval_*` 表 + Ragas（LLM-as-judge）承载**，与在线请求路径解耦，避免「trace 既要服务可观测性又要服务评估」的耦合。

## 接续

- **离线评估**：[[decisions/adr-014-ragas-integration]] · [[features/ragas-evaluation]]
- **在线 tracing**（不再承担评估责任）：[[decisions/adr-015-langfuse-tracing]]
- **未来打通**：ADR-014 / ADR-015 "v2 待办" 中均标记了 "Ragas eval replay 作为 trace + score 推进 LangFuse"

## 关联

- 实施期：[.planning/2026-05-18-agent-trace-eval/](#)
- 关联废止：[[decisions/adr-009-agent-trace-foundation]]
