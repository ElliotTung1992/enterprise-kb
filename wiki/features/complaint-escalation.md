---
created: 2026-05-15
tags: [feature, complaint, escalation, hitl, stategraph, plan-react]
---

# 功能：用户投诉升级 — Plan + ReAct 工作流

## 概述

投诉升级系统处理无法由普通客服解决的复杂投诉，采用 **Plan + ReAct** 组合模式，实现责任认定、方案制定、人工审批、自动执行的完整闭环。

核心能力：
- AI 自动收集数据 + 规则/LLM 混合责任认定
- 生成结构化处理计划（含 fallback 备选方案）
- 双 HITL 暂停点（DISPUTED 责任 + 计划审批）
- StateGraph 工作流编排，Redis checkpoint 跨会话持久化
- 超时检测 + 最多 2 次 Replanner 兜底，第 3 次升级高级专员

## 触发条件

| 普通投诉（客服直接处理） | 升级投诉（进入本系统） |
|------------------------|---------------------|
| 有标准解决方案 | 多次投诉未解决 |
| 单次对话可结案 | 涉及金额大或多方纠纷 |
| 客服可独立决策 | 需要商家/物流/平台多方协调 |

**所有升级投诉必须经过人工审批，无金额豁免。**

## 完整业务流程

```
╔════════════════════════════════════════════════════════════════════╗
║  Phase 7 — 客服入口（ReactAgent + escalateComplaint 工具）         ║
║                                                                    ║
║    用户对话描述投诉                                                ║
║         │                                                          ║
║         ▼                                                          ║
║    CustomerAssistant（ReactAgent）                                 ║
║         ├── 普通售后 ──→ checkAfterSalesEligibility               ║
║         │               → submitAfterSalesReview → HITL           ║
║         │                                                          ║
║         └── 识别升级投诉 ──→ escalateComplaint 工具               ║
║                                  │                                 ║
║                                  ▼                                 ║
║                   createComplaint()  投诉状态 → OPEN               ║
║                                  │                                 ║
║                                  ▼                                 ║
║                   complaintWorkflowService.startPlanning()         ║
╚════════════════════════════════════════════════════════════════════╝
                           │
                           ▼
╔════════════════════════════════════════════════════════════════════╗
║  StateGraph 工作流（ComplaintWorkflowServiceImpl）                  ║
║  threadId = planId（每次规划循环独立 Redis checkpoint）             ║
║  ★ 预插 shell 计划（AWAITING_RESPONSIBILITY）确保任意路径可查询    ║
║                                                                    ║
║  collectData → applyRules                                          ║
║                    ├─ 命中 → savePlan                             ║
║                    └─ 未命中 → llmInference                       ║
║                                    ├─ 非DISPUTED → savePlan       ║
║                                    └─ DISPUTED → humanAssignParty ║
║                                         ★ interruptBefore ①       ║
║                                         → 返回 AWAITING_RESPONSIBILITY
║                                         → POST assign-party 注入  ║
║                                              │                     ║
║                              savePlan（UPSERT，PENDING_REVIEW）    ║
║                                    ★ interruptBefore ②            ║
║                                    → applyDecision（HITL 审批）   ║
║                                              │                     ║
║                              ┌───────────────┴──────────────┐     ║
║                           execute                         close    ║
║                              │                               │     ║
║                        executePlan                       closePlan ║
║                        (ReactAgent)                               ║
╚════════════════════════════════════════════════════════════════════╝
                         │ 计划 EXECUTING 超时（next_check_at）
                         ▼
╔══════════════════════════════════════════════════════════════════╗
║  超时与 Replanner                                                 ║
║  ComplaintDeadlineScheduler（@Scheduled fixedDelay=60s）         ║
║       │                                                          ║
║       └── ComplaintReplannerService.replan()                     ║
║               ├── replanCount < 2                               ║
║               │   取 fallbackPlan[replanCount] → 新计划          ║
║               │   旧计划 → FAILED，新计划 → PENDING_REVIEW       ║
║               │                                                  ║
║               └── replanCount >= 2 或 ESCALATE_TO_SENIOR        ║
║                       投诉 → ESCALATED，旧计划 → FAILED          ║
╚══════════════════════════════════════════════════════════════════╝
```

## 关键组件

| 组件 | 路径 | 职责 |
|------|------|------|
| `ComplaintWorkflowService` | `kb-search/service/ComplaintWorkflowService.java` | StateGraph 编排，HITL 恢复入口 |
| `ComplaintWorkflowServiceImpl` | `kb-search/service/impl/ComplaintWorkflowServiceImpl.java` | 图节点实现，checkpoint 管理 |
| `ComplaintEscalationService` | `kb-search/service/ComplaintEscalationService.java` | 投诉/计划 CRUD，审批状态流转 |
| `ComplaintExecutorService` | `kb-search/service/ComplaintExecutorService.java` | ReactAgent 执行阶段 |
| `ComplaintReplannerService` | `kb-search/service/ComplaintReplannerService.java` | Fallback 重规划逻辑 |
| `ComplaintDeadlineScheduler` | `kb-search/scheduler/ComplaintDeadlineScheduler.java` | 超时扫描，触发 Replanner |
| `ComplaintController` | `kb-search/controller/ComplaintController.java` | REST API 端点 |

## StateGraph 节点与状态键

### 节点清单

| 节点 | 功能 |
|------|------|
| `collectData` | 加载投诉，提取订单/物流信号，投诉状态 → PLANNING |
| `applyRules` | 确定性规则判责（覆盖约 80% 案例） |
| `llmInference` | LLM 兜底推理责任方 + 理由 |
| `humanAssignParty` | HITL 暂停① — 等待人工指定责任方（DISPUTED 路径） |
| `savePlan` | UPSERT 完整计划，状态 → PENDING_REVIEW |
| `applyDecision` | HITL 暂停② — 读取审批结果，决定 execute/close |
| `executePlan` | ReactAgent 执行补偿动作序列 |
| `closePlan` | 记录关闭日志，流程结束 |

### 责任认定规则层

| 条件 | 责任方 |
|------|--------|
| 物流签收后投诉商品损坏 | MERCHANT |
| 物流轨迹中断超 48h | LOGISTICS |
| 系统订单状态异常 | PLATFORM |
| 签收 7 天内质量问题 | MERCHANT |
| 未发货超时 | MERCHANT |
| 规则未命中 | → LLM 推理，不确定则 DISPUTED |

### State 键清单

| 键 | 写入节点 | 类型 |
|---|---------|------|
| `complaintId` | 初始状态 | UUID.toString |
| `planId` | 初始状态 | UUID.toString（= threadId） |
| `orderStatus` | collectData | COMPLETED / SYSTEM_ABNORMAL / NOT_SHIPPED_OVERDUE |
| `logisticsDelivered` / `logisticsInterrupted` | collectData | "true"/"false" |
| `responsibleParty` | applyRules / llmInference | 枚举 name |
| `responsibilityReason` | applyRules / llmInference | 中文理由 |
| `reviewResult` | HITL updateState | "APPROVED" / "REJECTED" / "MODIFIED" |
| `modifiedParty` / `modifiedCompType` / `modifiedAmount` | HITL updateState | 可选，MODIFIED 时有效 |
| `nextAction` | applyDecision | "execute" / "close" |

## Planner 输出结构

```json
{
  "caseId": "COMPLAINT-xxx",
  "responsibleParty": "MERCHANT",
  "responsibilityReason": "LLM 推理理由（供审批员参考）",
  "compensation": { "amount": 150, "type": "REFUND_AND_COUPON" },
  "contactSequence": ["MERCHANT", "LOGISTICS"],
  "timeouts": { "merchantResponseDeadline": "48h", "onTimeout": "TRIGGER_REPLANNER" },
  "fallback": [
    { "responsibleParty": "PLATFORM", "compensation": { "amount": 200, "type": "REFUND" } },
    { "action": "ESCALATE_TO_SENIOR", "reason": "两次方案均失败" }
  ]
}
```

## 工具清单

### Planner 阶段（并行数据收集）

| 工具 | 用途 |
|------|------|
| `getComplaintHistory(userId)` | 历史投诉次数和结案记录 |
| `getOrderDetails(orderId)` | 订单金额、商品、商家 |
| `getLogisticsTrace(trackingNo)` | 物流轨迹和异常节点 |
| `getMerchantViolationRecord(merchantId)` | 商家历史违规 |
| `getCompensationPolicy()` | 平台赔偿上限规则 |

### ReAct 执行阶段

| 工具 | 用途 |
|------|------|
| `notifyParty` | 通知商家/物流/平台限期处理 |
| `executeCompensation` | 执行退款 / 发补偿券 |
| `recordResolution` | 记录结案信息 |
| `sendUserNotification` | 通知用户进展 |
| `escalateToSenior` | 升级高级专员 |

## 数据库表

| 表 | 迁移文件 | 说明 |
|----|---------|------|
| `complaints` | `022-add-complaint-escalation-tables.sql` | 投诉案件主表 |
| `complaint_plans` | `022-add-complaint-escalation-tables.sql` | AI 生成的处理计划（含 JSONB：contactSequence / timeouts / fallbackPlan） |
| `next_check_at` 字段 | `023-add-complaint-plan-next-check-at.sql` | 执行超时检测基准时间 |

## 状态枚举

**ComplaintStatus**：`OPEN → PLANNING → PENDING_REVIEW → EXECUTING → RESOLVED / ESCALATED / CLOSED`

**ComplaintPlanStatus**：`AWAITING_RESPONSIBILITY → PENDING_REVIEW → APPROVED / REJECTED → EXECUTING → COMPLETED / FAILED`

## 审批 API

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/plans/{id}/assign-party` | 人工指定责任方（DISPUTED HITL①） |
| `POST` | `/plans/{id}/approve` | 直接审批通过 |
| `POST` | `/plans/{id}/reject` | 拒绝，投诉 → CLOSED |
| `POST` | `/plans/{id}/modify-and-approve` | 修改责任方/补偿后审批 |
| `POST` | `/plans/{id}/trigger-timeout` | 手动触发超时重规划（测试用） |

## 前端

`complaint-review.html`（Bootstrap 5）：
- 状态 Tab 筛选（AWAITING_RESPONSIBILITY / PENDING_REVIEW / APPROVED 等）
- 计划列表：责任方 badge、补偿方案、计划状态
- 行内惰性加载投诉内容 + AI 推理理由 + fallback 列表
- 操作模态框：批准 / 改后批 / 拒绝 / 人工判责

## 与售后审核的对比

| 维度 | 售后审核 | 投诉升级 |
|------|----------|---------|
| Agent 模式 | 纯 ReAct | Plan + ReAct |
| 工作流编排 | HumanInTheLoopHook | Spring AI Alibaba StateGraph |
| HITL 对象 | 用户申请 | AI 生成的处理计划 |
| HITL 暂停点 | 1 个 | 2 个（判责 + 审批） |
| 重规划 | 无 | 最多 2 次（取 fallback） |
| 跨天执行 | 否 | 是（商家 48h 响应期） |
| 工具数量 | 2 个 | 13 个 |

## 相关页面

- [[features/hitl-after-sales]] — 售后审核（同一客服助手入口）
- [[decisions/adr-007-complaint-escalation-stategraph]] — Plan+ReAct 架构决策
- [[ai-rag/hitl-hook]] — HumanInTheLoopHook 机制
- [[database/entities/after-sales-tables]] — 客服相关数据表
