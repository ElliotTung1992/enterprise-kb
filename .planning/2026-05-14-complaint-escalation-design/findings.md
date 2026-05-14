# Findings & Decisions

## Requirements
- Phase 0 目标：确认现有售后、HITL、审核中心、客服助手相关代码如何复用。
- 需要输出：可复用代码、投诉升级入口模块、审核复用边界、Phase 1 数据模型方向。

## Research Findings
- 客服助手已从知识库问答中独立出来，入口为 `CustomerAssistantController`，路由前缀 `/api/v1/after-sales`，服务为 `CustomerAssistantServiceImpl`。
- `CustomerAssistantServiceImpl` 当前使用 `ReactAgent` + `HumanInTheLoopHook`，HITL 拦截工具名固定为 `submitAfterSalesReview`。
- 当前 HITL 流程是"工具调用被拦截 → 写入 `review_requests` → 审核员 approve/reject → `resumeWithFeedback` 恢复 Agent"。
- `review_requests` 当前强绑定被拦截工具调用：字段包含 `session_id`、`tool_call_id`、`tool_call_name`，`ReviewFeedbackHelper` 要求 `toolCallId` 非空才能恢复 Agent。
- 投诉升级审批与现有售后审核语义不同：投诉审批是"审批 Planner 生成的计划后启动 Executor"，不是"恢复被 HITL 中断的工具调用"。
- 审核中心前端 `reviews.html` 当前只支持 `AFTER_SALES` 类型，列表数据来自 `/after-sales/reviews`，审批操作调用 `/after-sales/reviews/{id}/approve|reject` 并期待返回 AI 通知客户文本。
- `ReviewStatus` 只有 `PENDING / APPROVED / REJECTED`，可用于审批状态，但不够表达投诉计划执行状态。
- Liquibase 当前最新变更为 `021-add-customer-sessions-composite-index.sql`，下一阶段新增迁移应从 `022-...` 开始。
- 投诉升级相关代码适合放在 `kb-search`：它已经承载 AI 编排、客户助手、审核请求、Redis checkpoint、静态客服入口的服务端 API。
- `kb-app` 仍负责 Liquibase 迁移和静态页面；若 Phase 6 改审核员前端，需要改 `kb-app/src/main/resources/static/reviews.html`。
- Phase 1 已落地基础数据模型：`complaints` 存投诉案件主线，`complaint_plans` 存 AI 生成的处理计划。
- `complaint_plans` 的 `contact_sequence`、`timeouts`、`fallback_plan` 使用 JSONB 存储，但 Java model 暂按项目现有 `ReviewRequest` 风格用 `String` 承载 JSON。

## Technical Decisions
| Decision | Rationale |
|----------|-----------|
- 投诉升级后端先放在 `kb-search` 模块 | 复用现有 AI 编排、客服助手、审核服务和 MyBatis 配置，避免新模块引入额外依赖边界。 |
- Phase 1 新增 `complaints` / `complaint_plans` 独立业务表 | 投诉计划需要责任方、推理理由、fallback、重规划次数、执行状态等字段，塞进 `review_requests` 会污染售后审核模型。 |
- `review_requests` 可复用为"审批队列"，但不作为投诉计划主表 | 审核中心已有列表、状态和审批操作；但投诉计划主数据必须由 `complaint_plans` 持有。 |
- 投诉审批不要复用 `resumeWithFeedback` 作为核心路径 | 现有 resume 依赖 `toolCallId` 和被中断工具调用；投诉计划审批通过后应调用 Executor，而不是恢复一个售后 Agent。 |
- Phase 3 可扩展 `ReviewRequest` 加 `review_type` / `target_id` / `payload` | 用于让同一个审核中心承载 `AFTER_SALES` 与 `COMPLAINT_PLAN`，并路由到不同审批处理器。 |
- Phase 1 先实现服务层与数据持久化，不改现有售后流程 | 降低风险，确保所有现有 `/after-sales` 行为保持不变。 |
- `ComplaintEscalationService.savePlan()` 保存计划后将投诉状态推进为 `PENDING_REVIEW` | 与 Planner MVP 产物进入人工审核的业务流一致，Phase 3 再接入真正审核请求。 |

## Issues Encountered
| Issue | Resolution |
|-------|------------|
- 现有审核通过/拒绝会先调用 LLM 恢复 Agent，再更新 DB；这不适合投诉计划审批 | Phase 3 设计独立的投诉审批处理逻辑：批准后更新计划状态并触发 Executor，拒绝后更新计划状态或进入重规划。 |
- 审核中心前端写死售后类型和接口 | Phase 6 再做前端泛化；Phase 1-4 可以先用后端测试和最小 API 验证闭环。 |

## Resources
- `kb-search/src/main/java/com/enterprise/kb/search/service/impl/CustomerAssistantServiceImpl.java`
- `kb-search/src/main/java/com/enterprise/kb/search/controller/CustomerAssistantController.java`
- `kb-search/src/main/java/com/enterprise/kb/search/controller/ReviewController.java`
- `kb-search/src/main/java/com/enterprise/kb/search/controller/ReviewFeedbackHelper.java`
- `kb-search/src/main/java/com/enterprise/kb/search/model/ReviewRequest.java`
- `kb-search/src/main/java/com/enterprise/kb/search/service/ReviewRequestService.java`
- `kb-search/src/main/resources/mapper/ReviewRequestMapper.xml`
- `kb-app/src/main/resources/db/changelog/018-add-review-requests.sql`
- `kb-app/src/main/resources/db/changelog/db.changelog-master.xml`
- `kb-app/src/main/resources/static/reviews.html`
- `kb-app/src/main/resources/db/changelog/022-add-complaint-escalation-tables.sql`
- `kb-search/src/main/java/com/enterprise/kb/search/service/ComplaintEscalationService.java`
- `kb-search/src/main/java/com/enterprise/kb/search/service/impl/ComplaintEscalationServiceImpl.java`
- `kb-search/src/test/java/com/enterprise/kb/search/service/impl/ComplaintEscalationServiceImplTest.java`
