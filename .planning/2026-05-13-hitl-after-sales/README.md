# 售后 HITL（2026-05-13）

`AgenticQnAServiceImpl` 集成 Human-in-the-Loop：AI 引导用户填表 → 触发人工审核 → 审核员决策后回调用户的完整闭环。

## 状态

Phase 1–6 完成，Phase 7 集成测试待执行。后被 `AfterSalesDomainHandler` 包入两层域路由（见 [2026-05-18-intent-routing/](../2026-05-18-intent-routing/)）。

## 文件

- [task_plan.md](task_plan.md)
- [progress.md](progress.md)
- [findings.md](findings.md)

## 关联 wiki

- [[features/hitl-after-sales]]
- [[decisions/adr-005-hitl-transaction-ordering]]
