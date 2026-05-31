# 投诉升级 API

基础路径：`/api/v1/complaints`

控制器：`ComplaintController`。所有接口需要有效 JWT Token。

由 `ComplaintWorkflowService` 通过 StateGraph 工作流编排（`collectData → applyRules → [llmInference →] savePlan → humanAssignParty? → applyDecision`），HITL 暂停在 `humanAssignParty` 或 `applyDecision` 节点。详见 [[features/complaint-escalation]] · [[decisions/adr-007-complaint-escalation-stategraph]]。

## 投诉案件接口

### `GET /api/v1/complaints/{complaintId}` — 查询投诉案件详情

返回 `Complaint` 实体。

### `POST /api/v1/complaints?orderId=...&content=...` — 创建投诉

| 参数 | 类型 | 说明 |
|------|------|------|
| `orderId` | query | 关联订单号 |
| `content` | query | 投诉内容 |

返回新建 `Complaint` 实体。

### `POST /api/v1/complaints/{complaintId}/plan` — 启动 AI 工作流规划

执行 `collectData → applyRules → [llmInference →] savePlan`：
- LLM 判定为 `DISPUTED` → 在 `humanAssignParty` 节点暂停，返回 `AWAITING_RESPONSIBILITY` 计划
- 否则 → 在 `applyDecision` 节点暂停，返回 `PENDING_REVIEW` 计划

## 处理计划接口

### `GET /api/v1/complaints/plans?status=...` — 查询计划列表

`status` 为 `ComplaintPlanStatus` 枚举，不传返回全部。

### `GET /api/v1/complaints/{complaintId}/plans` — 查询指定案件下的所有计划

### `POST /api/v1/complaints/plans/{planId}/assign-party` — 人工指定责任方（DISPUTED 流程）

```json
{ "responsibleParty": "MERCHANT|PLATFORM|USER", "reason": "..." }
```

工作流从 `humanAssignParty` 节点继续，经 `savePlan` 后再次暂停于 `applyDecision`。

### `POST /api/v1/complaints/plans/{planId}/approve` — 审批通过

```json
{ "comment": "审批意见（可选）" }
```

工作流自动恢复并执行补偿方案。

### `POST /api/v1/complaints/plans/{planId}/reject` — 拒绝计划

```json
{ "comment": "拒绝原因（可选）" }
```

工作流恢复并关闭投诉案件。

### `POST /api/v1/complaints/plans/{planId}/modify-and-approve` — 修改计划后审批通过

```json
{ "responsibleParty": "...", "compensationAmount": 100, "compensationDescription": "...", "comment": "..." }
```

工作流自动恢复并执行修改后的方案。

### `POST /api/v1/complaints/plans/{planId}/trigger-timeout` — 触发超时重规划

从 fallback 列表取下一方案创建新计划并进入审批；若已达最大重规划次数则升级至高级专员。

## 相关页面

- [[features/complaint-escalation]] — 完整业务流程与 StateGraph
- [[decisions/adr-007-complaint-escalation-stategraph]] — 架构决策
