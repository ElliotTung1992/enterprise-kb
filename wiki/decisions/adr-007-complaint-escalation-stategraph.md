---
created: 2026-05-15
tags: [adr, architecture, complaint, stategraph, plan-react, hitl]
---

# ADR-007：投诉升级采用 Plan + ReAct + StateGraph 架构

**状态**：已采用

## 背景

用户投诉升级场景具备以下特征，使其无法套用现有售后审核（纯 ReAct + HumanInTheLoopHook）模式：

1. **跨天执行**：商家有 48h 响应期，Agent 不能持续运行，需要跨会话状态持久化
2. **多方协调**：商家、物流、平台各自独立，执行结果不可预知
3. **策略级审批**：操作员需一次审批完整策略（责任认定理由 + 方案 + 备选路径），不能逐步审批每个动作
4. **重规划需求**：方案执行失败后需从 fallback 中取次选方案，最多 2 次，第 3 次强制升级人工专员

## 决策

### 1. Plan + ReAct 分层

- **Planner 阶段**：StateGraph 节点负责数据收集、规则判责、LLM 兜底、计划落库
- **ReAct 执行阶段**：计划审批通过后，`ComplaintExecutorService` 用 ReactAgent 驱动执行动作序列

**真正让 Planner 不可替代的理由**：人工审批的对象是"策略"，不是"动作"。Planner 把责任方、补偿方案、备选路径、推理理由压缩成一份文档，操作员一次批完，后续全自动执行。没有 Planner 则只能在每个动作前单独审批，粒度过细且流程不可预测。

### 2. StateGraph 代替 HumanInTheLoopHook

现有 `HumanInTheLoopHook` 专为"工具调用被拦截后恢复同一 Agent"设计，依赖 `toolCallId` 和 `sessionId`，不适合"审批一个已落库计划后启动全新执行器"的语义。

StateGraph 优势：
- 命名节点 + 条件边，流程可观测
- `interruptBefore` 精确控制暂停点
- `CompiledGraph.updateState()` + `stream(null, config)` 实现干净的 HITL 恢复
- 每次规划循环独立 `threadId`（= planId），历史 checkpoint 互不干扰

### 3. 双 HITL 暂停点

| 暂停点 | 节点 | 触发条件 | 恢复 API |
|--------|------|---------|---------|
| ① 人工判责 | `humanAssignParty` | LLM 推理为 DISPUTED | `POST /plans/{id}/assign-party` |
| ② 计划审批 | `applyDecision` | 所有路径的 savePlan 之后 | `POST /plans/{id}/approve` 等 |

### 4. Shell 计划预插（解决 DISPUTED 路径 404 问题）

`startPlanning()` 在调用 `stream()` 前预插状态为 `AWAITING_RESPONSIBILITY` 的 shell 计划记录。`savePlan` 节点改为 PostgreSQL UPSERT（`ON CONFLICT (id) DO UPDATE`），确保无论走哪条路径，`findPlanById(planId)` 始终能命中记录。

如果不预插 shell：DISPUTED 路径下图在 `humanAssignParty` 前暂停，`savePlan` 未执行，前端调 `GET /plans/{id}` 得到 404。

### 5. 责任认定混合模式

规则层覆盖约 80% 的确定性案例，LLM 仅处理边界案例，不确定时输出 `DISPUTED` 直接触发 HITL①，不猜测。这保证了确定性和可审计性。

### 6. Replanner 限制为 2 次

- `replanCount < 2`：从 `fallbackPlan[replanCount]` 取下一方案，不重走整个流程
- `replanCount >= 2`：强制 `ESCALATE_TO_SENIOR`，避免无限循环
- 超时检测由 `ComplaintDeadlineScheduler`（60s 轮询）触发，不依赖 Agent 自判

## 后果

**优点**：
- 流程可观测（每个节点状态清晰）
- HITL 恢复语义干净（不依赖售后工具调用机制）
- Replanner 上限确定，系统行为可预期
- StateGraph checkpoint 天然支持跨天持久化

**权衡**：
- 复杂度高于纯 ReAct；适合本场景，不适合简单单次对话场景
- 所有 State 值必须序列化为 String（UUID.toString / 枚举.name），确保 RedisSaver 安全

## 相关决策

- [[decisions/adr-005-hitl-transaction-ordering]] — 售后审核的事务顺序问题（投诉升级未复用该模式）
- [[decisions/adr-006-customer-assistant-separation]] — 客服助手独立化

## 相关功能

- [[features/complaint-escalation]] — 完整功能文档
- [[features/hitl-after-sales]] — 售后审核（对比参考）
