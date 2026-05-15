# Task Plan: 用户投诉升级 — Plan + ReAct 架构设计

## Goal
设计商城"用户投诉升级"场景的 AI 处理架构，采用 Plan + ReAct 组合模式，实现责任认定、方案制定、多方协商、自动执行的完整闭环。

## Status: ALL PHASES COMPLETE (Phase 0-8)

---

## 完整业务流程图（Phase 9 — 双 HITL 暂停点版）

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
║  Phase 8+9 — StateGraph 工作流（ComplaintWorkflowServiceImpl）     ║
║                                                                    ║
║  POST /{complaintId}/plan  →  startPlanning(complaintId)           ║
║  threadId = planId（每次规划循环独立 Redis checkpoint）             ║
║  ★ 预插 shell 计划（AWAITING_RESPONSIBILITY）确保任意路径可查询    ║
║                                                                    ║
║  ┌─── Node: collectData ────────────────────────────────────────┐  ║
║  │  加载 Complaint，投诉状态 → PLANNING                          │  ║
║  │  提取 orderStatus / logisticsDelivered / interrupted 等信号  │  ║
║  └──────────────────────────────┬─────────────────────────────  ┘  ║
║                                 │                                   ║
║  ┌─── Node: applyRules ─────────────────────────────────────────┐  ║
║  │  已签收+损坏 → MERCHANT  │  物流中断48h → LOGISTICS           │  ║
║  │  系统异常   → PLATFORM   │  7天内质量   → MERCHANT           │  ║
║  │  未发货超时 → MERCHANT                                        │  ║
║  │  命中 → 写 responsibleParty/reason   未命中 → 空 map          │  ║
║  └──────────────┬──────────────────────────────────────────────  ┘  ║
║                 │                                                   ║
║        ┌────────┴────────┐                                          ║
║      命中              未命中                                        ║
║        │                 │                                          ║
║        │  ┌─── Node: llmInference ──────────────────────────────┐  ║
║        │  │  调 responsibilityInferenceService 推理责任方 + 理由  │  ║
║        │  └─────────────────────┬──────────────────────────────  ┘  ║
║        │                        │                                   ║
║        │               ┌────────┴────────┐                         ║
║        │           非DISPUTED          DISPUTED                     ║
║        │               │                 │                         ║
║        │               │  ┌─── Node: humanAssignParty ──────────┐  ║
║        │               │  │  日志 + 校验责任方已注入              │  ║
║        │               │  └─────────────────────────────────────┘  ║
║        │               │                 │                         ║
║        │               │  ★ interruptBefore("humanAssignParty") ①  ║
║        │               │    → 返回 AWAITING_RESPONSIBILITY 计划     ║
║        │               │    → POST assign-party 注入责任方后恢复   ║
║        │               │                 │                         ║
║        └───────────────┴─────────────────┘                         ║
║                                 │                                   ║
║  ┌─── Node: savePlan ───────────────────────────────────────────┐  ║
║  │  构建完整 ComplaintPlan（责任方/补偿/序列/fallback）           │  ║
║  │  UPSERT 覆写 shell 记录 → 计划状态 PENDING_REVIEW            │  ║
║  └──────────────────────────────┬─────────────────────────────  ┘  ║
║                                 │                                   ║
║          ★ interruptBefore("applyDecision") ②                      ║
║            → 返回 PENDING_REVIEW 计划；审批员操作后恢复             ║
║                                                                    ║
╚════════════════════════════════════════════════════════════════════╝
                           │ 审批员在 complaint-review.html 操作
                           ▼
╔════════════════════════════════════════════════════════════════════╗
║  Phase 3+6+9 — HITL 审批（审核员前端 → 恢复工作流）               ║
║                                                                    ║
║  POST /plans/{id}/assign-party        → resumeWithPartyAssignment()║
║  POST /plans/{id}/approve             → resumeApproved()           ║
║  POST /plans/{id}/reject              → resumeRejected()           ║
║  POST /plans/{id}/modify-and-approve  → resumeModified()           ║
║                                                                    ║
║  每个方法均执行：                                                  ║
║    compiledGraph.updateState(config, updates)                      ║
║    compiledGraph.stream(null, config).blockLast()                  ║
║    → 从 Redis 恢复 checkpoint，从暂停节点继续执行                  ║
╚════════════════════════════════════════════════════════════════════╝
                         │
                         ▼
╔══════════════════════════════════════════════════════════════════╗
║  ┌─────── Node: applyDecision ──────────────────────────────┐   ║
║  │  读取 reviewResult 分三路：                               │   ║
║  │                                                           │   ║
║  │  "APPROVED"  → approvePlan()  → nextAction = "execute"   │   ║
║  │  "MODIFIED"  → modifyAndApprovePlan()                     │   ║
║  │                              → nextAction = "execute"    │   ║
║  │  "REJECTED"  → rejectPlan()   → nextAction = "close"     │   ║
║  │               投诉 → CLOSED                               │   ║
║  └──────────────────────────┬───────────────────────────────┘   ║
║                             │                                    ║
║              ┌──────────────┴──────────────┐                    ║
║         "execute"                       "close"                 ║
║              │                             │                    ║
║              ▼                             ▼                    ║
║  ┌── Node: executePlan ──┐     ┌── Node: closePlan ──────────┐  ║
║  │  complaintExecutor    │     │  记录关闭日志，流程结束       │  ║
║  │  Service.execute()    │     └────────────────────────────┘  ║
║  │                       │                                      ║
║  │  ReactAgent 执行：    │                                      ║
║  │  ① notifyParty        │                                      ║
║  │  ② executeCompensation│                                      ║
║  │  ③ recordResolution   │                                      ║
║  │  ④ sendUserNotification│                                     ║
║  │                       │                                      ║
║  │  计划 → COMPLETED     │                                      ║
║  │  投诉 → RESOLVED      │                                      ║
║  └───────────┬───────────┘                                      ║
║              │                                                   ║
║              ▼                                                   ║
║            END                                                   ║
╚══════════════════════════════════════════════════════════════════╝
                         │ 计划 EXECUTING 超时
                         ▼
╔══════════════════════════════════════════════════════════════════╗
║  Phase 5 — 超时与 Replanner                                      ║
║                                                                  ║
║  ComplaintDeadlineScheduler（@Scheduled fixedDelay=60s）         ║
║  扫描 next_check_at 超时的 EXECUTING 计划                        ║
║       │                                                          ║
║       ▼                                                          ║
║  complaintWorkflowService.triggerReplan(planId)                  ║
║       │                                                          ║
║       └── ComplaintReplannerService.replan()                     ║
║               │                                                  ║
║               ├── replanCount < 2                               ║
║               │   从 fallbackPlan[replanCount] 取下一方案       ║
║               │   savePlan() 生成新计划（PENDING_REVIEW）        ║
║               │   旧计划 → FAILED，replanCount++                ║
║               │   审核员重新审批新计划（进入 HITL 流程）          ║
║               │                                                  ║
║               └── replanCount >= 2 或 ESCALATE_TO_SENIOR       ║
║                       投诉 → ESCALATED                           ║
║                       旧计划 → FAILED                            ║
║                       移交高级专员（流程结束）                   ║
╚══════════════════════════════════════════════════════════════════╝
```

---

## Agent 模式选型原则

> **流程长 + 需要多方配合 + 需要人审批整体策略 → Plan + ReAct**
>
> **实时对话 + 单方交互 + 决策即时生效 → 纯 ReAct**

| | 售后审核（已实现）| 投诉升级（本设计）|
|--|-----------------|----------------|
| 流程长度 | 单次对话内完成 | 跨天（48h 等待）|
| 多方配合 | 用户 + 审核员 | 用户 + 商家 + 物流 + 平台 |
| 人审批什么 | 用户的申请 | AI 生成的整体策略 |
| 模式 | ReAct | Plan + ReAct |

两个场景卡在边界两侧，对比清晰。

**注意**：以下说法是早期误判，已修正：
- ~~ReAct 上下文会丢失~~（单会话内上下文完整，跨会话靠 checkpoint 两种模式都能解决）
- ~~ReAct 无法按时间触发~~（外部调度器 + checkpoint resume 完全可行）
- ~~重试次数 ReAct 无法约束~~（state 字段也能记录）

**真正让 Planner 不可替代的理由**：人工审批的对象是"策略"，不是"动作"。Planner 把责任方、补偿方案、备选路径、推理理由压缩成一份文档，操作员一次批完，后续全自动执行。没有 Planner 则只能在每个动作前单独审批，粒度过细。

## 为什么这个场景适合 Plan + ReAct

- **流程跨天**：商家 48h 响应期，Agent 不可能持续运行，需要跨会话状态管理
- **多方协调**：商家、物流、平台各自独立，执行结果不可预知，需要备选路径
- **策略级审批**：操作员需要一次性看到完整策略（责任认定理由 + 方案 + 备选），而非逐步审批每个动作
- **并行数据收集**：5个数据源同时拉取，Planner 阶段天然并行

---

## 触发条件（普通投诉 vs 升级投诉）

| 普通投诉 | 升级投诉（进入本系统） |
|---------|-------------------|
| 客服可直接处理 | 多次投诉未解决 |
| 有标准解决方案 | 涉及金额大 |
| 单次对话可结案 | 涉及多方纠纷（商家+物流+平台）|
| | 需要跨部门协调 |

**所有升级投诉都必须经过人工审批**（无金额阈值豁免）。

---

## Planner 输出结构

```json
{
  "caseId": "COMPLAINT-xxx",
  "responsibleParty": "MERCHANT",
  "responsibilityReason": "LLM 推理理由（供审批员参考）",
  "compensation": {
    "amount": 150,
    "type": "REFUND_AND_COUPON"
  },
  "contactSequence": ["MERCHANT", "LOGISTICS"],
  "timeouts": {
    "merchantResponseDeadline": "48h",
    "onTimeout": "TRIGGER_REPLANNER"
  },
  "fallback": [
    {
      "responsibleParty": "PLATFORM",
      "compensation": { "amount": 200, "type": "REFUND" },
      "reason": "商家拒绝承担，平台垫付"
    },
    {
      "action": "ESCALATE_TO_SENIOR",
      "reason": "两次方案均失败，需高级专员介入"
    }
  ]
}
```

---

## 责任认定逻辑（混合模式）

### 规则层（覆盖约 80% 案例，确定性）

| 条件 | 责任方 |
|------|--------|
| 物流签收后投诉商品损坏 | 商家 |
| 物流轨迹中断超 48h | 物流 |
| 系统订单状态异常 | 平台 |
| 签收 7 天内质量问题 | 商家 |
| 未发货超时 | 商家 |

### LLM 层（处理规则覆盖不到的边界案例）

- 输入：投诉内容 + 所有收集数据 + 规则库
- 输出：责任方 + 判定理由（必须输出理由，供人工审批参考）
- 不确定时输出：`"responsibleParty": "DISPUTED"` → 直接走 HITL 人工判定

---

## 工具清单

### Planner 阶段（并行调用）

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
| `notifyMerchant(merchantId, caseId, deadline)` | 通知商家限期处理 |
| `notifyLogistics(trackingNo, claimType)` | 向物流索赔 |
| `executeRefund(orderId, amount)` | 执行退款 |
| `issueCoupon(userId, amount)` | 发补偿券 |
| `escalateToSenior(caseId, reason)` | 升级高级专员 |
| `checkDeadline(caseId)` | 检查是否超时，触发重规划 |
| `recordResolution(caseId, outcome)` | 记录结案信息 |
| `sendUserNotification(userId, message)` | 通知用户进展 |

---

## 关键设计决策

| 决策点 | 结论 | 理由 |
|--------|------|------|
| 联系顺序 | 规则固定 | 不需要智能，需要确定性 |
| 责任认定 | 混合（规则 + LLM） | 规则覆盖 80%，LLM 处理边界案例 |
| HITL 触发 | 所有升级投诉必须审批 | 无金额豁免，风险控制优先 |
| 审批对象 | 计划本身（含推理理由） | 人工审"推理过程"而非盲审 |
| 超时逻辑 | 写在计划 `timeouts` 字段 | 不靠 ReAct 自判，确定性优先 |
| 重规划次数 | 最多 2 次 | 第 3 次强制升级高级专员 |
| 重规划内容 | 只改责任方 + 补偿金额 | 不重走整个流程，取 fallback |
| 数据收集 | Planner 阶段并行 | 节省时间，所有数据一次到位 |
| LLM 不确定时 | 输出 DISPUTED → 直接 HITL | 不猜，让人判 |

---

## 与售后审核的对比

| 维度 | 售后审核（已实现） | 投诉升级（本设计）|
|------|-----------------|----------------|
| Agent 模式 | 纯 ReAct | Plan + ReAct |
| 流程复杂度 | 6步，2个分支 | 6阶段，3方协调，循环 |
| HITL 审批对象 | 用户申请 | AI 生成的计划 |
| 重规划 | 无 | 最多 2 次（取 fallback）|
| 跨天执行 | 否 | 是（商家 48h 响应期）|
| 工具数量 | 2个 | 13个 |

---

## 实现阶段（按 MVP 闭环拆分）

### 实施原则

- 第一版先做"升级投诉闭环"，不追求真实商家/物流外部系统联动。
- 优先复用现有 HITL / `review_requests` 能力，避免一开始新造完整审批体系。
- Planner 和 ReAct Executor 先做服务层能力，再接前端。
- 最小可交付版本到 Phase 4：创建升级投诉 → 生成计划 → 人工审批 → 模拟执行 → 结案。
- Phase 5-7 将可演示闭环升级为更接近真实业务的完整流程。

### Phase 0: 现状摸底与边界确认
**Status**: complete

目标：确认现有售后、HITL、审核中心、客服助手相关代码如何复用。

实现内容：
- 梳理现有 `CustomerAssistantServiceImpl`
- 梳理 `ReviewRequest` / HITL Hook / 审核前端
- 确认投诉升级入口放在哪个模块
- 确认是否复用现有审核表，还是新增投诉计划表

验收标准：
- 明确哪些现有代码可复用
- 形成最终表结构和服务边界

Phase 0 结论：
- 复用模块：投诉升级后端放在 `kb-search`，前端与迁移仍由 `kb-app` 承载。
- 复用能力：客户助手会话、AI 模型解析、审核中心列表与 `ReviewStatus` 可复用。
- 不直接复用：现有 `resumeWithFeedback` 只适合被 HITL 中断的售后工具调用，不适合作为投诉计划审批通过后的执行入口。
- Phase 1 数据模型方向：新增 `complaints` / `complaint_plans` 作为主业务表；`review_requests` 后续可扩展为审批队列表，但不承载投诉计划主数据。

### Phase 1: 投诉升级基础数据模型
**Status**: complete

目标：让系统能持久化一个升级投诉案例和 AI 计划。

建议新增：
- `complaints`
- `complaint_plans`
- 必要枚举或常量：责任方、计划状态、补偿类型、执行状态

最小字段：
- 投诉编号、用户、订单、投诉内容、状态
- 责任方、责任理由、补偿类型、补偿金额
- 联系顺序、超时策略、fallback、重规划次数
- 审批状态、执行状态

验收标准：
- Liquibase 能迁移成功
- Mapper / Service 能创建投诉和保存计划
- 单元测试覆盖创建与查询

Phase 1 结果：
- 新增 Liquibase 迁移 `022-add-complaint-escalation-tables.sql`，创建 `complaints` / `complaint_plans`。
- 新增投诉状态、计划状态、责任方、补偿类型常量。
- 新增 `Complaint` / `ComplaintPlan` model、Mapper XML、`ComplaintEscalationService`。
- 新增 `ComplaintEscalationServiceImplTest` 覆盖创建投诉、保存计划默认值、查询异常和计划列表。

### Phase 2: Planner MVP
**Status**: complete

目标：输入投诉内容和订单号，生成一个结构化计划。

实现内容：
- 新增 `ComplaintPlannerService.generatePlan(caseId)`
- 收集投诉历史、订单详情、物流轨迹、商家违规记录、赔偿政策
- 先实现硬编码责任规则
- 规则无法命中时，再调用 LLM 输出责任方和理由
- 计划状态为 `PENDING_REVIEW`

验收标准：
- 明确案例能命中规则并生成计划
- 边界案例能走 LLM
- 不确定案例输出 `DISPUTED`
- 计划落库成功

Phase 2 结果：
- 新增 `ComplaintPlannerService.generatePlan(complaintId)`。
- 新增 Planner 数据上下文 DTO 和责任认定结果 DTO。
- 新增 `ComplaintResponsibilityInferenceService` 作为规则未命中时的 LLM 兜底接口。
- Planner MVP 使用 mock 数据收集器，先实现确定性规则，再走 LLM 推理。
- 生成计划后落库为 `PENDING_REVIEW`，并通过 `ComplaintEscalationService.savePlan()` 推进投诉状态。
- 单元测试覆盖规则命中、物流责任、LLM 边界案例、DISPUTED 不确定案例和计划落库。

### Phase 3: HITL 审批接入
**Status**: complete

目标：所有升级投诉计划必须人工审批后才能执行。

实现内容：
- 创建审核请求，审核对象是 `complaint_plan`
- 审核内容展示责任方、责任理由、补偿方案、fallback、数据依据
- 支持批准、拒绝、修改后批准
- 第一版"修改后批准"只允许改责任方、补偿金额、备注

验收标准：
- 计划生成后自动进入待审核
- 未审批不能执行
- 审批通过后计划状态变为 `APPROVED`
- 拒绝后状态变为 `REJECTED` 或回到待重规划

Phase 3 结果：
- 新增 `ComplaintPlanModifyRequest` DTO，支持局部字段修改（responsibleParty / compensationType / compensationAmount / comment），未传字段保持原值。
- `ComplaintPlanMapper` 新增 `updateFields()` 方法，Mapper XML 使用 `<set>/<if>` 动态跳过 null 字段。
- `ComplaintEscalationService` 新增 `approvePlan` / `rejectPlan` / `modifyAndApprovePlan` 三个接口方法。
- `ComplaintEscalationServiceImpl` 实现上述三个方法，均加前置状态校验 `requirePendingReview()`，非 PENDING_REVIEW 状态操作抛 400。
- `ComplaintController` 新增三个 endpoint：
  - `POST /plans/{planId}/approve` — 直接审批通过
  - `POST /plans/{planId}/reject` — 拒绝并关闭投诉（状态 CLOSED）
  - `POST /plans/{planId}/modify-and-approve` — 修改字段后审批通过
- Phase 4 的执行入口已强制校验 APPROVED 状态，确保未审批计划不可执行。

### Phase 4: ReAct Executor MVP
**Status**: complete

目标：审批通过后，按计划执行一条最小闭环。

第一版先做内部模拟工具：
- `notifyMerchant`
- `notifyLogistics`
- `executeRefund`
- `issueCoupon`
- `recordResolution`
- `sendUserNotification`

执行流程：
- 按 `contactSequence` 创建执行记录
- 模拟通知商家/物流
- 模拟退款/补券
- 记录结案
- 通知用户

验收标准：
- 只有 `APPROVED` 计划能执行
- 执行成功后投诉状态变为 `RESOLVED`
- 每一步有执行日志
- 失败不会吞异常，有明确状态

Phase 4 结果：
- 新增 `ComplaintExecutorService` 接口，唯一方法 `execute(UUID planId)`。
- 新增 `ComplaintExecutorServiceImpl`，使用 `ReactAgent` 驱动执行：
  - 前置校验：仅 APPROVED 状态可执行，否则抛 400。
  - 状态流转：进入执行时置 EXECUTING，成功后置 COMPLETED + 投诉 RESOLVED，失败置 FAILED。
  - 4 个 mock 工具：`notifyParty`（通用化，支持 MERCHANT / LOGISTICS / PLATFORM）、`executeCompensation`（REFUND / COUPON / REFUND_AND_COUPON）、`recordResolution`、`sendUserNotification`。
  - 工具输入类型定义为私有内部 record（`NotifyPartyInput` 等），与项目已有模式一致。
  - `runAgent()` 声明 `throws GraphRunnerException`，catch 块捕获 `GraphRunnerException | KbException`，不吞异常。
  - ReactAgent 设置 `recursionLimit = 15`，防止无限循环。
- 新增 `ComplaintExecutionResult` DTO（record：planId / complaintId / summary）。
- `ComplaintController` 新增 `POST /plans/{planId}/execute` endpoint。
- 编译验证通过（`mvn install -pl kb-search -am -DskipTests`）。

### Phase 5: 超时与 Replanner
**Status**: complete

目标：把 48h 等待、fallback、最多 2 次重规划做成确定状态机。

实现内容：
- 增加 `next_check_at`
- 增加定时任务或手动触发检查
- 实现 `checkDeadline(caseId)`
- 超时或拒绝时触发 Replanner
- Replanner 只从 `fallback` 里取下一方案
- `replan_count >= 2` 后强制 `escalateToSenior`

验收标准：
- 未超时不重规划
- 超时后取 fallback
- 最多重规划 2 次
- 第 3 次失败自动升级高级专员
- 状态流转可追踪

Phase 5 结果：
- DB 迁移 `023-add-complaint-plan-next-check-at.sql`：给 `complaint_plans` 加 `next_check_at TIMESTAMPTZ` 字段。
- `KnowledgeBaseApplication` 加 `@EnableScheduling`。
- `ComplaintPlan` model 加 `nextCheckAt` 字段，Mapper XML resultMap 同步更新。
- `ComplaintPlanMapper` 新增 `startExecution()`（同时写 status=EXECUTING 和 next_check_at）、`findExecutingPastDeadline(now)`（查超时计划）。
- `ComplaintEscalationService` 新增 `startPlanExecution(planId, nextCheckAt)`，实现类落库。
- `ComplaintExecutorServiceImpl.execute()` 改用 `startPlanExecution(planId, now+48h)`，替换原来仅更新 status 的调用。
- 新增 `ComplaintReplannerService` 接口 + `ComplaintReplannerServiceImpl`：
  - 解析当前计划 `fallbackPlan` JSON；
  - 用 `replanCount` 作为 fallback 索引；
  - `replanCount >= 2` 或遇到 `ESCALATE_TO_SENIOR` 条目 → 投诉置 ESCALATED，计划置 FAILED；
  - 否则从 fallback 取新责任方/补偿，调 `savePlan()` 生成新计划（PENDING_REVIEW，replanCount+1），旧计划置 FAILED。
- 新增 `ComplaintDeadlineScheduler`：`@Scheduled(fixedDelay=60_000)` 扫描超时 EXECUTING 计划，逐一调 `replan()`，单条失败不影响其他计划。
- `ComplaintController` 新增 `POST /plans/{id}/trigger-timeout` 手动触发重规划（测试用）。
- 编译验证通过。

### Phase 6: 审核员前端
**Status**: complete

目标：让审核员能看懂 AI 为什么这么判，并能做审批操作。

Phase 6 结果：
- 新增 `complaint-review.html`（Bootstrap 5）：
  - 左侧侧边栏同步新增"投诉升级审核"入口，`reviews.html` / `customer.html` 同步追加侧边栏链接。
  - 顶部状态 Tab 筛选（PENDING_REVIEW / APPROVED / REJECTED / EXECUTING+COMPLETED+FAILED / 全部）。
  - 计划列表表格：案件ID、订单号、责任方 badge、补偿方案、计划状态 badge、生成时间、操作列。
  - 行内展开详情（惰性加载投诉内容、AI 推理理由、补偿明细、fallback 备选列表）。
  - 操作按钮（仅 PENDING_REVIEW 显示）：批准 / 改后批 / 拒绝。
  - "直接批准"/ "拒绝"模态框：含审批意见文本框。
  - "修改后批准"模态框：责任方 select / 补偿类型 select / 补偿金额 number / 意见文本框。
  - Toast 通知成功/失败。
- `ComplaintController` 新增 `GET /api/v1/complaints/{complaintId}` 查询投诉详情（供前端惰性加载）。
- `index.html` 新增"投诉升级审核"入口卡片。
- 全量编译验证通过。

### Phase 7: 客服入口与端到端联调
**Status**: complete

目标：把能力接入客服助手，让用户投诉升级能从对话进入闭环。

Phase 7 结果：
- `CustomerAssistantServiceImpl` 新增 `escalateComplaint` FunctionToolCallback：
  - 输入：`orderId`（订单号）、`description`（投诉描述）。
  - 调用链：`complaintEscalationService.createComplaint()` → `complaintWorkflowService.startPlanning()`（Phase 8 后更新）。
  - 成功后返回中文确认字符串（含投诉 ID），由客服助手反馈给用户。
  - 参数空值校验抛 KbException(400)，AI 工具链异常透传。
- 系统提示词更新：加入升级投诉识别指引（多次联系商家无果 / 涉及金额 ≥300 / 多方纠纷 / 用户明确要求升级）、升级流程步骤（多轮收集订单号和描述 → 调用 escalateComplaint → 告知用户已提交等待审核）。
- 端到端联调路径：用户对话 → 客服助手 escalateComplaint → 投诉落库（OPEN）→ 工作流生成计划（PENDING_REVIEW）→ 审核员在 complaint-review.html 审批 → 工作流自动执行 → RESOLVED 或 ESCALATED。

### Phase 8: StateGraph 工作流编排重构
**Status**: complete

目标：将 Phase 2（Planner）+ Phase 3（HITL 审批）+ Phase 4（Executor）+ Phase 5（Replanner 入口）整合进 Spring AI Alibaba `StateGraph` 工作流编排，实现统一的状态机驱动、节点可观测、HITL 暂停/恢复。

**核心变更：**

新增 `ComplaintWorkflowService` 接口 + `ComplaintWorkflowServiceImpl`：

- `StateGraph` 编译为 `CompiledGraph`，`@PostConstruct` 初始化一次，多请求复用；
- 7 个命名节点：`collectData` → `applyRules` → `[llmInference]` → `savePlan` → `applyDecision` → `executePlan` / `closePlan`；
- 条件边（`addConditionalEdges`）：
  - `applyRules` 后：state 有 `responsibleParty` → `savePlan`，无 → `llmInference`；
  - `applyDecision` 后：`nextAction=="execute"` → `executePlan`，否则 → `closePlan`；
- `CompileConfig.interruptBefore("applyDecision")`：`savePlan` 完成后自动暂停，状态以 `planId` 为 `threadId` 持久化到 Redis；
- `startPlanning(complaintId)` 预生成 `planId` → `stream(initialState, config).blockLast()` → 返回 PENDING_REVIEW 计划；
- `resumeApproved/resumeRejected/resumeModified` → `updateState(config, updates)` + `stream(null, config).blockLast()` 恢复图执行；
- 所有 state 值均为 `String`（UUID.toString、枚举 name、BigDecimal.toPlainString），确保 RedisSaver 序列化安全；
- `KeyStrategyFactoryBuilder.defaultStrategy(new ReplaceStrategy())` 统一覆盖写策略。

**同步更新：**
- `ComplaintController`：注入 `ComplaintWorkflowService`，移除 `ComplaintPlannerService` / `ComplaintExecutorService` / `ComplaintReplannerService` 直接依赖，删除独立的 `POST /plans/{id}/execute` 端点（审批通过即自动执行）；
- `CustomerAssistantServiceImpl.escalateComplaint()`：改调 `complaintWorkflowService.startPlanning()`；
- `ComplaintDeadlineScheduler`：改调 `complaintWorkflowService.triggerReplan()`；
- `ComplaintPlannerService` / `ComplaintExecutorService` 接口及实现保留，不再被 controller 直接注入（graph 节点内部调用 executor，planner 逻辑迁移到 graph 节点）。

**State 键清单：**

| 键 | 写入节点 | 说明 |
|---|---------|------|
| `complaintId` | 初始状态 | UUID.toString |
| `planId` | 初始状态 | UUID.toString，即 threadId |
| `complaintContent` | collectData | 投诉内容原文 |
| `orderId` | collectData | 订单号 |
| `orderAmount` | collectData | "299.00"（mock） |
| `orderStatus` | collectData | COMPLETED / SYSTEM_ABNORMAL / NOT_SHIPPED_OVERDUE |
| `logisticsDelivered` | collectData | "true"/"false" |
| `logisticsInterrupted` | collectData | "true"/"false" |
| `logisticsWithin7Days` | collectData | "true"/"false" |
| `responsibleParty` | applyRules / llmInference | 枚举 name |
| `responsibilityReason` | applyRules / llmInference | 中文理由 |
| `reviewResult` | HITL（updateState） | "APPROVED" / "REJECTED" / "MODIFIED" |
| `reviewerId` | HITL（updateState） | UUID.toString |
| `reviewComment` | HITL（updateState） | 意见文本 |
| `modifiedParty` | HITL（updateState，可选） | 枚举 name |
| `modifiedCompType` | HITL（updateState，可选） | 枚举 name |
| `modifiedAmount` | HITL（updateState，可选） | BigDecimal 字符串 |
| `nextAction` | applyDecision | "execute" / "close" |

**验收结果：**
- `mvn install -pl kb-search -am -DskipTests` BUILD SUCCESS；
- 全部 46 个单元测试通过（0 Failures, 0 Errors）。

### Phase 9: DISPUTED → 第一个 HITL 暂停（人工判责）
**Status**: complete

**背景**：Phase 8 完成后，`llmInference` 推理为 DISPUTED 时，仍然直接走 `savePlan` 保存零赔付空方案，进入 `applyDecision` HITL。APPROVED 路径会执行一个无意义的空计划；必须 MODIFIED 才能真正修复责任方。设计上不够清晰，且 `startPlanning()` 在 DISPUTED 路径下若图先于 `savePlan` 暂停会导致 `findPlanById` 查不到记录。

**设计方案**：
```
applyRules (规则命中) ──────────────────→ savePlan
applyRules (未命中) → llmInference
    ├─ 非DISPUTED ──────────────────────→ savePlan
    └─ DISPUTED → humanAssignParty  ★ interruptBefore 暂停①
                       ↓ 人工指定责任方 resumeWithPartyAssignment()
                   savePlan  (UPSERT 覆写 shell 记录)
                       ↓
                   applyDecision  ★ interruptBefore 暂停②
                       ↓
               executePlan / closePlan
```

**核心问题与解法**：
- `startPlanning()` 在 DISPUTED 路径下 `stream().blockLast()` 停在 `humanAssignParty` 前，此时 `savePlan` 节点尚未执行，`findPlanById(planId)` 会报 404。
- **解法**：`startPlanning()` 在调用 `stream()` 前预插 shell 计划记录（状态 `AWAITING_RESPONSIBILITY`），`savePlan` 节点改为 UPSERT（INSERT ... ON CONFLICT DO UPDATE），保证 `findPlanById` 任何路径均能命中。

**变更清单**：
- `ComplaintPlanStatus`：新增 `AWAITING_RESPONSIBILITY`
- `ComplaintPlanMapper.xml`：`insert` 改为 PostgreSQL upsert（ON CONFLICT (id) DO UPDATE）
- `ComplaintWorkflowService`：新增 `resumeWithPartyAssignment(planId, party, reason)`
- `ComplaintPartyAssignmentRequest`：新增请求 DTO（record）
- `ComplaintWorkflowServiceImpl`：
  - `startPlanning()` 预插 shell 计划
  - 新增 `humanAssignParty` 节点
  - `llmInference` 后改为条件边：DISPUTED → `humanAssignParty`，否则 → `savePlan`
  - 编译时 `interruptBefore("humanAssignParty", "applyDecision")`
  - 实现 `resumeWithPartyAssignment()`
- `ComplaintController`：新增 `POST /plans/{planId}/assign-party`
- `complaint-review.html`：新增 `AWAITING_RESPONSIBILITY` 状态展示与"人工判责"操作模态框

**State 键新增**：无，仍复用 `responsibleParty` / `responsibilityReason`

**验收标准**：
- 规则命中路径：不受影响，shell 被 UPSERT 覆写
- 非 DISPUTED LLM 路径：不受影响，shell 被 UPSERT 覆写
- DISPUTED 路径：`startPlanning()` 返回 AWAITING_RESPONSIBILITY 计划；`assign-party` 恢复后流入 `savePlan` 生成 PENDING_REVIEW；`approve` 后执行
- 编译通过，单元测试全部通过
