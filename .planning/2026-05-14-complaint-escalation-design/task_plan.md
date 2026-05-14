# Task Plan: 用户投诉升级 — Plan + ReAct 架构设计

## Goal
设计商城"用户投诉升级"场景的 AI 处理架构，采用 Plan + ReAct 组合模式，实现责任认定、方案制定、多方协商、自动执行的完整闭环。

## Status: DESIGN COMPLETE — 待实现

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

## 完整架构

```
┌─────────────────────────────────────────────────────┐
│ PLANNER                                             │
│                                                     │
│  并行数据收集：                                      │
│  getComplaintHistory + getOrderDetails +            │
│  getLogisticsTrace + getMerchantViolationRecord     │
│  + getCompensationPolicy                           │
│           ↓                                         │
│  责任认定：规则先行 → 模糊案例 LLM 推理 + 输出理由    │
│           ↓                                         │
│  生成结构化计划（含备选和超时）                       │
└──────────────────┬──────────────────────────────────┘
                   ↓
┌─────────────────────────────────────────────────────┐
│ HITL：人工审批计划（审推理过程，不是盲审）            │
│  ✓ 批准 → 进入执行                                  │
│  ✏️ 修改 → 调整方案后执行                           │
│  ✗ 拒绝 → 返回 Planner 重新规划                    │
└──────────────────┬──────────────────────────────────┘
                   ↓
┌─────────────────────────────────────────────────────┐
│ REACT EXECUTOR                                      │
│                                                     │
│  阶段1: notifyMerchant/notifyLogistics              │
│         → checkDeadline(48h)                       │
│    ├─► 接受 → 阶段2: executeRefund / issueCoupon   │
│    └─► 超时/拒绝 → 触发 Replanner                  │
│                         ↑                           │
│  [Replanner 取 fallback] ← 最多触发 2 次            │
│    ├─► 方案B执行成功 → 阶段3: 用户回访确认           │
│    └─► 第2次仍失败 → escalateToSenior               │
└─────────────────────────────────────────────────────┘
```

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
**Status**: pending

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

### Phase 3: HITL 审批接入
**Status**: pending

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

### Phase 4: ReAct Executor MVP
**Status**: pending

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

### Phase 5: 超时与 Replanner
**Status**: pending

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

### Phase 6: 审核员前端
**Status**: pending

目标：让审核员能看懂 AI 为什么这么判，并能做审批操作。

页面重点：
- 投诉基本信息
- 订单、物流、历史投诉摘要
- AI 责任认定
- 推理理由
- 补偿方案
- fallback
- 审批按钮
- 修改后批准入口

验收标准：
- 审核员能完成批准、拒绝、修改后批准
- 展示的是"计划 + 理由"，不是只展示一句结论
- 状态刷新正确

### Phase 7: 客服入口与端到端联调
**Status**: pending

目标：把能力接入客服助手，让用户投诉升级能从对话进入闭环。

实现内容：
- 客服助手识别升级投诉
- 收集订单号和投诉描述
- 创建 `complaint`
- 调用 Planner
- 告知用户"已提交升级投诉，等待专员审核"
- 审批、执行、结案全链路联调

验收标准：
- 从用户对话到投诉结案跑通
- 人工审批不可跳过
- 计划、审核、执行、通知都有记录
- 失败路径能进入重规划或高级专员
