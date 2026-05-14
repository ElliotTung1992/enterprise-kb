# Task Plan: 商城客服助手流程重设计

## Goal
重设计 CustomerAssistantServiceImpl 的业务流程与工具层，支持基于订单状态动态确定售后类型（退款/换货/退货退款），并强制二次确认后才提交审核。

## Status: DESIGN COMPLETE — 待实现

---

## 架构决策：纯 ReAct，无需 Planner

**结论**：增强工具设计即可，不引入 Plan-and-Execute 层。

### 选型原则

> **流程长 + 需要多方配合 + 需要人审批整体策略 → Plan + ReAct**
>
> **实时对话 + 单方交互 + 决策即时生效 → 纯 ReAct**

本场景属于后者：单次对话内完成，用户与审核员两方，审批对象是用户申请而非 AI 策略。

工具的触发条件本身形成隐式顺序约束，LLM 跳步会导致参数缺失，不依赖 Planner 也能保证执行顺序。

---

## 新业务流程

### 路径 A：用户只说"我要售后"
```
① 识别售后意图
② 引导用户提供订单号
③ checkAfterSalesEligibility(orderId) → 结构化结果
④ 展示所有 allowedActions，让用户选择
⑤ 用户选择 + 可选补充说明
⑥ 展示确认文案，等待用户明确确认
⑦ confirmAndSubmit(chosenAction, notes) → HITL
```

### 路径 B：用户说"订单 1111，我要退款"
```
① 识别意图 + 提取订单号（同一轮）
③ checkAfterSalesEligibility(orderId) → 结构化结果
④-a allowedActions 含用户意图 → "您申请退款 ¥299，确认吗？"
④-b allowedActions 不含用户意图 → "退款不可用，可选：换货/退货退款"
⑥ 等待用户明确确认
⑦ confirmAndSubmit(chosenAction, notes) → HITL
```

**不变的规则**：
- ③ checkAfterSalesEligibility 永远执行
- ⑥ 二次确认永远执行
- ⑦ 用户明确确认前不得调用 confirmAndSubmit

---

## Phase 1: 工具层重设计
**Status**: pending

### checkAfterSalesEligibility — 改为返回结构化数据

现状：返回自然语言文本
改为：返回结构化 JSON（新增 `AfterSalesEligibilityResult` record）

```json
{
  "orderId": "ORD-123",
  "shippingStatus": "NOT_SHIPPED",
  "allowedActions": ["REFUND"],
  "orderAmount": 299.00,
  "orderSummary": "商品尚未发货，可申请全额退款",
  "withinReturnWindow": true
}
```

shippingStatus 枚举 → allowedActions 映射：
- NOT_SHIPPED → ["REFUND"]
- SHIPPED_IN_TRANSIT → ["INTERCEPT_REFUND"]
- DELIVERED + 7天内 → ["RETURN_REFUND", "EXCHANGE"]
- DELIVERED + 超7天 → []

### confirmAndSubmit — 新工具，替代 submitAfterSalesReview

工具描述中写明触发条件（约束 LLM）：
```
仅在以下条件全部满足时调用：
1. 已调用 checkAfterSalesEligibility 且 chosenAction 在 allowedActions 中
2. 已向用户展示确认文案
3. 用户明确表示同意（"确认"、"好的"等肯定语义）
用户追问、改变主意、犹豫时，不得调用。
```

参数：chosenAction（REFUND/EXCHANGE/RETURN_REFUND/INTERCEPT_REFUND）+ notes（AI摘要+用户补充）

HITL Hook 改为监控 `confirmAndSubmit`。

### 任务清单
- [ ] 新增 `AfterSalesEligibilityResult` record DTO
- [ ] 修改 `checkAfterSalesEligibility` 返回 `AfterSalesEligibilityResult`
- [ ] 新增 `ConfirmAndSubmitInput` record DTO
- [ ] 更新 `buildAgent()` 替换工具
- [ ] 更新 HITL Hook 监控工具名（submitAfterSalesReview → confirmAndSubmit）
- [ ] 重写 System Prompt

---

## Phase 2: 数据模型变更
**Status**: pending

### review_requests 新增 notes 字段

```sql
-- 021-add-notes-to-review-requests.sql
ALTER TABLE review_requests ADD COLUMN notes TEXT;
```

notes 语义：
- 追加式 TEXT，不锁格式
- AI 写入初始摘要（含申请类型、原因、金额）
- 用户可追加补充说明
- 不加 request_type enum（业务规则可能扩展，notes 文本已足够审核员判断）

### 任务清单
- [ ] 新增 `021-add-notes-to-review-requests.sql`
- [ ] 更新 `db.changelog-master.xml`
- [ ] 更新 `ReviewRequest` model（新增 notes 字段）
- [ ] 更新 `ReviewRequestMapper` + XML
- [ ] 更新 `ReviewRequestService.createPending()` 含 notes 参数

---

## Phase 3: System Prompt 重写
**Status**: pending

要点：
- 描述两条路径（A：只说售后 / B：带订单号和意图）
- 明确工具触发时机和禁止条件
- 强调二次确认为必须步骤，不可跳过
- 描述 notes 格式（"申请类型：退款 | 订单：ORD-123 | 金额：¥299 | 原因：..."）

---

## Phase 4: 前端审核中心适配
**Status**: pending

- 审核申请卡片主展示 `notes` 字段
- `conversation_snapshot` 保留为"查看详情"展开项

---

## Errors Encountered
| Error | Attempt | Resolution |
|-------|---------|------------|
| — | — | — |

---

## Decisions Log
| 决策点 | 结论 |
|--------|------|
| Plan vs ReAct | 纯 ReAct，工具设计提供隐式顺序约束，Planner 过重 |
| 谁判断意图与可选项匹配 | LLM（语义匹配是其优势，后端只提供结构化数据） |
| 二次确认机制 | 独立工具 `confirmAndSubmit`，描述中写明触发条件 |
| request_type 字段 | 不加，改为追加式 `notes` TEXT 字段 |
| notes 填写方 | AI 初始写入摘要，用户可追加，不锁死 |
| checkAfterSalesEligibility 返回格式 | 结构化 JSON record，不返回自然语言 |
