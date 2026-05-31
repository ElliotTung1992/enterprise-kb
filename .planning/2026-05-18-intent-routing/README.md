# 客服助手意图识别 — 两层域路由（2026-05-18）

商城客服助手从「单 ReactAgent + 隐式意图」重构为两层域路由：Tier-1 `DomainRouterService` 分类到业务域，Tier-2 `DomainHandler` 选工具。准确率归属分离，意图识别首次可度量。

## 状态

Phase 0–3 全部实现，全项目编译通过，76 个单元测试通过。**未上线**：评估集为模板（待运营测试同学标注）、影子数据为空（待部署）、迁移 024 未落库、端到端 & HITL 回归未跑。kill-switch `enterprise.kb.customer-assistant.routing-enabled` 默认 false，旧单体路径仍生效。

## 文件

- [plan.md](plan.md) — 实施计划全文（含 Phase 0 评估集 / Phase 1 路由器 / Phase 2 域处理器 / Phase 3 影子+灰度）

## 关联 wiki

- [[features/intent-routing]]
- [[decisions/adr-008-intent-routing-two-tier]]
- 被包入：[[features/hitl-after-sales]]（`AfterSalesDomainHandler`） · [[features/complaint-escalation]]（`ComplaintDomainHandler`）
