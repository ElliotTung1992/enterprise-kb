# 商城客服助手流程重设计（2026-05-14）

重设计 `CustomerAssistantServiceImpl` 业务流程与工具层：基于订单状态动态确定售后类型（退款/换货/退货退款），强制二次确认后才提交审核。

## 状态

设计完成。实现并未按本计划单独落地——后续被 [2026-05-18-intent-routing/](../2026-05-18-intent-routing/) 的两层域路由方案覆盖、扩展。

## 文件

- [task_plan.md](task_plan.md)
- [progress.md](progress.md)
- [findings.md](findings.md)

## 关联 wiki

- [[decisions/adr-006-customer-assistant-separation]]
- [[features/intent-routing]]（最终实现形态）
