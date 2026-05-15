package com.enterprise.kb.search.service.impl;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.enterprise.kb.common.constants.CompensationType;
import com.enterprise.kb.common.constants.ComplaintStatus;
import com.enterprise.kb.common.constants.ResponsibleParty;
import com.enterprise.kb.common.exception.KbException;
import com.enterprise.kb.search.dto.ComplaintPlanModifyRequest;
import com.enterprise.kb.search.dto.ComplaintPlanningContext;
import com.enterprise.kb.search.dto.ComplaintResponsibilityDecision;
import com.enterprise.kb.search.model.Complaint;
import com.enterprise.kb.search.model.ComplaintPlan;
import com.enterprise.kb.search.service.ComplaintEscalationService;
import com.enterprise.kb.search.service.ComplaintExecutorService;
import com.enterprise.kb.search.service.ComplaintReplannerService;
import com.enterprise.kb.search.service.ComplaintResponsibilityInferenceService;
import com.enterprise.kb.search.service.ComplaintWorkflowService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 投诉处理工作流服务实现。
 *
 * <p>使用 Spring AI Alibaba {@code StateGraph} 编排投诉处理全流程：
 * <pre>
 * START → collectData → applyRules → [conditional] → savePlan（规则命中）
 *                                  → llmInference → [conditional] → savePlan（非 DISPUTED）
 *                                                               → humanAssignParty（DISPUTED）
 * [interruptBefore humanAssignParty（HITL 暂停①，等待人工判责）]
 * → humanAssignParty → savePlan
 * [interruptBefore applyDecision（HITL 暂停②，等待人工审批）]
 * → applyDecision → [conditional] → executePlan → END
 *                                 → closePlan → END
 * </pre>
 * 每次规划循环以 {@code planId} 作为 {@code threadId}，状态持久化到 Redis。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintWorkflowServiceImpl implements ComplaintWorkflowService {

    private static final BigDecimal DEFAULT_ORDER_AMOUNT = new BigDecimal("299.00");
    private static final BigDecimal MAX_REFUND_AMOUNT = new BigDecimal("500.00");
    private static final BigDecimal MAX_COUPON_AMOUNT = new BigDecimal("50.00");

    private final ComplaintEscalationService complaintEscalationService;
    private final ComplaintExecutorService complaintExecutorService;
    private final ComplaintReplannerService complaintReplannerService;
    private final ComplaintResponsibilityInferenceService responsibilityInferenceService;
    private final RedisSaver agentCheckpointSaver;
    private final ObjectMapper objectMapper;

    private CompiledGraph compiledGraph;

    @PostConstruct
    public void init() {
        try {
            this.compiledGraph = buildWorkflowGraph();
            log.info("投诉处理工作流图编译完成");
        } catch (GraphStateException e) {
            throw new IllegalStateException("投诉处理工作流图编译失败", e);
        }
    }

    // ---- 公开服务方法 ----

    /**
     * {@inheritDoc}
     */
    @Override
    public ComplaintPlan startPlanning(UUID complaintId) {
        UUID planId = UUID.randomUUID();

        // 预插 shell，DISPUTED 路径暂停于 humanAssignParty 前时 findPlanById 仍可命中；
        // savePlan 节点通过 UPSERT 覆写为完整计划；不触发投诉状态变更（collectData 负责）
        complaintEscalationService.insertShellPlan(planId, complaintId);

        RunnableConfig config = RunnableConfig.builder()
                .threadId(planId.toString())
                .build();
        Map<String, Object> initialState = Map.of(
                "complaintId", complaintId.toString(),
                "planId", planId.toString()
        );

        log.info("投诉处理工作流启动：complaintId={}，planId={}", complaintId, planId);
        compiledGraph.stream(initialState, config).blockLast();

        return complaintEscalationService.findPlanById(planId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ComplaintPlan resumeApproved(UUID planId, UUID reviewerId, String comment) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("reviewResult", "APPROVED");
        updates.put("planId", planId.toString());
        updates.put("reviewerId", reviewerId.toString());
        updates.put("reviewComment", comment != null ? comment : "");
        resumeWorkflow(planId, updates);
        return complaintEscalationService.findPlanById(planId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ComplaintPlan resumeRejected(UUID planId, UUID reviewerId, String comment) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("reviewResult", "REJECTED");
        updates.put("planId", planId.toString());
        updates.put("reviewerId", reviewerId.toString());
        updates.put("reviewComment", comment != null ? comment : "");
        resumeWorkflow(planId, updates);
        return complaintEscalationService.findPlanById(planId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ComplaintPlan resumeModified(UUID planId, UUID reviewerId, ComplaintPlanModifyRequest req) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("reviewResult", "MODIFIED");
        updates.put("planId", planId.toString());
        updates.put("reviewerId", reviewerId.toString());
        updates.put("reviewComment", req.comment() != null ? req.comment() : "");
        if (req.responsibleParty() != null) {
            updates.put("modifiedParty", req.responsibleParty().name());
        }
        if (req.compensationType() != null) {
            updates.put("modifiedCompType", req.compensationType().name());
        }
        if (req.compensationAmount() != null) {
            updates.put("modifiedAmount", req.compensationAmount().toPlainString());
        }
        resumeWorkflow(planId, updates);
        return complaintEscalationService.findPlanById(planId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ComplaintPlan resumeWithPartyAssignment(UUID planId, UUID assignerId, ResponsibleParty party, String reason) {
        if (party == ResponsibleParty.DISPUTED) {
            throw new KbException("人工判责不允许再次指定 DISPUTED", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("planId", planId.toString());
        updates.put("responsibleParty", party.name());
        updates.put("responsibilityReason", reason != null ? reason : "");
        updates.put("assignerId", assignerId.toString());
        resumeWorkflow(planId, updates);
        return complaintEscalationService.findPlanById(planId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void triggerReplan(UUID planId) {
        complaintReplannerService.replan(planId);
    }

    // ---- 工作流图构建 ----

    private CompiledGraph buildWorkflowGraph() throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = new KeyStrategyFactoryBuilder()
                .defaultStrategy(new ReplaceStrategy())
                .build();

        StateGraph graph = new StateGraph(keyStrategyFactory)
                .addNode("collectData", node_async(this::collectData))
                .addNode("applyRules", node_async(this::applyRules))
                .addNode("llmInference", node_async(this::llmInference))
                .addNode("humanAssignParty", node_async(this::humanAssignParty))
                .addNode("savePlan", node_async(this::savePlan))
                .addNode("applyDecision", node_async(this::applyDecision))
                .addNode("executePlan", node_async(this::executePlan))
                .addNode("closePlan", node_async(this::closePlan))
                .addEdge(START, "collectData")
                .addEdge("collectData", "applyRules")
                .addConditionalEdges("applyRules",
                        edge_async(state -> state.<String>value("responsibleParty").isPresent()
                                ? "savePlan" : "llmInference"),
                        Map.of("savePlan", "savePlan", "llmInference", "llmInference"))
                .addConditionalEdges("llmInference",
                        edge_async(state -> "DISPUTED".equals(state.value("responsibleParty", ""))
                                ? "humanAssignParty" : "savePlan"),
                        Map.of("humanAssignParty", "humanAssignParty", "savePlan", "savePlan"))
                .addEdge("humanAssignParty", "savePlan")
                .addEdge("savePlan", "applyDecision")
                .addConditionalEdges("applyDecision",
                        edge_async(state -> "execute".equals(state.value("nextAction", "close"))
                                ? "executePlan" : "closePlan"),
                        Map.of("executePlan", "executePlan", "closePlan", "closePlan"))
                .addEdge("executePlan", END)
                .addEdge("closePlan", END);

        return graph.compile(CompileConfig.builder()
                .saverConfig(SaverConfig.builder().register(agentCheckpointSaver).build())
                .interruptBefore("humanAssignParty", "applyDecision")
                .build());
    }

    // ---- 工作流节点实现 ----

    /**
     * collectData 节点：加载投诉数据，将上下文提取为状态键值，供后续节点使用。
     */
    private Map<String, Object> collectData(OverAllState state) {
        String complaintIdStr = state.<String>value("complaintId")
                .orElseThrow(() -> new KbException("工作流状态缺少 complaintId", HttpStatus.INTERNAL_SERVER_ERROR));
        UUID complaintId = UUID.fromString(complaintIdStr);

        Complaint complaint = complaintEscalationService.findComplaintById(complaintId);
        complaintEscalationService.updateComplaintStatus(complaintId, ComplaintStatus.PLANNING);

        String content = complaint.getContent() != null ? complaint.getContent() : "";
        String normalized = content.toLowerCase();

        String orderStatus = "COMPLETED";
        if (containsAny(normalized, "系统", "状态异常")) {
            orderStatus = "SYSTEM_ABNORMAL";
        } else if (containsAny(normalized, "未发货", "不发货")) {
            orderStatus = "NOT_SHIPPED_OVERDUE";
        }

        boolean interrupted = containsAny(normalized, "物流中断", "轨迹中断", "48小时", "48h");
        boolean delivered = containsAny(normalized, "签收", "收到", "到货", "损坏", "破损", "质量");
        boolean deliveredWithin7Days = delivered && !containsAny(normalized, "超过7天", "超7天");

        log.info("工作流[collectData]：complaintId={}，orderStatus={}，delivered={}，interrupted={}",
                complaintId, orderStatus, delivered, interrupted);

        return Map.of(
                "complaintContent", content,
                "orderId", complaint.getOrderId() != null ? complaint.getOrderId() : "",
                "orderAmount", DEFAULT_ORDER_AMOUNT.toPlainString(),
                "orderStatus", orderStatus,
                "logisticsDelivered", String.valueOf(delivered),
                "logisticsInterrupted", String.valueOf(interrupted),
                "logisticsWithin7Days", String.valueOf(deliveredWithin7Days)
        );
    }

    /**
     * applyRules 节点：执行确定性规则判断责任方。规则命中时写入 responsibleParty，否则返回空 map 触发 LLM 推理。
     */
    private Map<String, Object> applyRules(OverAllState state) {
        String content = state.value("complaintContent", "");
        String normalized = content.toLowerCase();
        String orderStatus = state.value("orderStatus", "COMPLETED");
        boolean delivered = Boolean.parseBoolean(state.value("logisticsDelivered", "false"));
        boolean interrupted = Boolean.parseBoolean(state.value("logisticsInterrupted", "false"));
        boolean within7Days = Boolean.parseBoolean(state.value("logisticsWithin7Days", "false"));

        if (delivered && containsAny(normalized, "损坏", "破损")) {
            return decision("MERCHANT", "物流签收后投诉商品损坏，按规则由商家承担责任。");
        }
        if (interrupted) {
            return decision("LOGISTICS", "物流轨迹中断超过 48 小时，按规则由物流承担责任。");
        }
        if ("SYSTEM_ABNORMAL".equals(orderStatus)) {
            return decision("PLATFORM", "系统订单状态异常，按规则由平台承担责任。");
        }
        if (within7Days && containsAny(normalized, "质量", "故障", "坏了", "无法使用")) {
            return decision("MERCHANT", "签收 7 天内反馈质量问题，按规则由商家承担责任。");
        }
        if ("NOT_SHIPPED_OVERDUE".equals(orderStatus)) {
            return decision("MERCHANT", "订单未发货且已超时，按规则由商家承担责任。");
        }

        log.info("工作流[applyRules]：规则未命中，转 LLM 推理");
        return Map.of();
    }

    /**
     * llmInference 节点：规则未命中时，调用 LLM 进行责任方推理。
     */
    private Map<String, Object> llmInference(OverAllState state) {
        String complaintIdStr = state.<String>value("complaintId")
                .orElseThrow(() -> new KbException("工作流状态缺少 complaintId", HttpStatus.INTERNAL_SERVER_ERROR));

        // collectData 已把 content/orderId 写入 state，直接从 state 构建避免重复查 DB
        Complaint complaint = new Complaint();
        complaint.setId(UUID.fromString(complaintIdStr));
        complaint.setContent(state.value("complaintContent", ""));
        complaint.setOrderId(state.value("orderId", ""));
        ComplaintPlanningContext context = buildContextFromState(state);

        ComplaintResponsibilityDecision d = responsibilityInferenceService.infer(complaint, context);
        log.info("工作流[llmInference]：complaintId={}，责任方={}，理由={}",
                complaintIdStr, d.responsibleParty(), d.responsibilityReason());

        return decision(d.responsibleParty().name(), d.responsibilityReason());
    }

    private Map<String, Object> humanAssignParty(OverAllState state) {
        String party = state.value("responsibleParty", "DISPUTED");
        String reason = state.value("responsibilityReason", "");
        String assignerId = state.value("assignerId", "unknown");
        log.info("工作流[humanAssignParty]：assignerId={}，责任方={}，理由={}", assignerId, party, reason);
        if (ResponsibleParty.DISPUTED.name().equals(party)) {
            throw new KbException("责任方仍为 DISPUTED，人工判责未正确注入", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return Map.of();
    }

    /**
     * savePlan 节点：根据责任方决策构建投诉处理计划并持久化到数据库。
     * <p>通过 UPSERT 覆写 startPlanning() 预插的 shell 记录，确保 DISPUTED 路径也能正确保存完整计划。</p>
     */
    private Map<String, Object> savePlan(OverAllState state) {
        String complaintIdStr = state.<String>value("complaintId")
                .orElseThrow(() -> new KbException("工作流状态缺少 complaintId", HttpStatus.INTERNAL_SERVER_ERROR));
        String planIdStr = state.<String>value("planId")
                .orElseThrow(() -> new KbException("工作流状态缺少 planId", HttpStatus.INTERNAL_SERVER_ERROR));
        UUID complaintId = UUID.fromString(complaintIdStr);
        UUID planId = UUID.fromString(planIdStr);

        String partyStr = state.value("responsibleParty", "DISPUTED");
        String reason = state.value("responsibilityReason", "责任方待确定");
        String orderAmountStr = state.value("orderAmount", DEFAULT_ORDER_AMOUNT.toPlainString());

        ResponsibleParty party = ResponsibleParty.valueOf(partyStr);
        BigDecimal orderAmount = new BigDecimal(orderAmountStr);

        ComplaintPlan plan = new ComplaintPlan();
        plan.setId(planId);
        plan.setComplaintId(complaintId);
        plan.setResponsibleParty(party);
        plan.setResponsibilityReason(reason);
        plan.setCompensationType(compensationTypeFor(party));
        plan.setCompensationAmount(party == ResponsibleParty.DISPUTED
                ? BigDecimal.ZERO : orderAmount.min(MAX_REFUND_AMOUNT));
        plan.setContactSequence(toJson(contactSequenceFor(party)));
        plan.setTimeouts(toJson(Map.of(
                "merchantResponseDeadline", "48h",
                "onTimeout", "TRIGGER_REPLANNER"
        )));
        plan.setFallbackPlan(toJson(buildFallbackPlan(party)));
        plan.setReplanCount(0);

        complaintEscalationService.savePlan(plan);
        log.info("工作流[savePlan]：planId={}，complaintId={}，责任方={}", planId, complaintId, party);
        return Map.of();
    }

    /**
     * applyDecision 节点：执行人工审批决策（审批通过/拒绝/修改后通过），并设置后续路由标记。
     * <p>本节点前有 interruptBefore 暂停点，graph 恢复后才执行此节点。</p>
     */
    private Map<String, Object> applyDecision(OverAllState state) {
        String planIdStr = state.<String>value("planId")
                .orElseThrow(() -> new KbException("工作流状态缺少 planId", HttpStatus.INTERNAL_SERVER_ERROR));
        String reviewerIdStr = state.<String>value("reviewerId")
                .orElseThrow(() -> new KbException("工作流状态缺少 reviewerId", HttpStatus.INTERNAL_SERVER_ERROR));
        UUID planId = UUID.fromString(planIdStr);
        UUID reviewerId = UUID.fromString(reviewerIdStr);

        String reviewResult = state.value("reviewResult", "REJECTED");
        String comment = state.value("reviewComment", "");

        switch (reviewResult) {
            case "APPROVED" -> {
                complaintEscalationService.approvePlan(planId, reviewerId, comment);
                log.info("工作流[applyDecision]：计划审批通过 planId={}", planId);
                return Map.of("nextAction", "execute");
            }
            case "MODIFIED" -> {
                String modifiedParty = state.value("modifiedParty", (String) null);
                String modifiedCompType = state.value("modifiedCompType", (String) null);
                String modifiedAmount = state.value("modifiedAmount", (String) null);
                ComplaintPlanModifyRequest req = new ComplaintPlanModifyRequest(
                        modifiedParty != null ? ResponsibleParty.valueOf(modifiedParty) : null,
                        modifiedCompType != null ? CompensationType.valueOf(modifiedCompType) : null,
                        modifiedAmount != null ? new BigDecimal(modifiedAmount) : null,
                        comment
                );
                complaintEscalationService.modifyAndApprovePlan(planId, reviewerId, req);
                log.info("工作流[applyDecision]：计划修改后审批通过 planId={}", planId);
                return Map.of("nextAction", "execute");
            }
            default -> {
                complaintEscalationService.rejectPlan(planId, reviewerId, comment);
                log.info("工作流[applyDecision]：计划审批拒绝 planId={}", planId);
                return Map.of("nextAction", "close");
            }
        }
    }

    /**
     * executePlan 节点：调用 ReactAgent 执行已审批的投诉处理计划。
     */
    private Map<String, Object> executePlan(OverAllState state) {
        String planIdStr = state.<String>value("planId")
                .orElseThrow(() -> new KbException("工作流状态缺少 planId", HttpStatus.INTERNAL_SERVER_ERROR));
        UUID planId = UUID.fromString(planIdStr);
        log.info("工作流[executePlan]：开始执行 planId={}", planId);
        complaintExecutorService.execute(planId);
        return Map.of();
    }

    /**
     * closePlan 节点：投诉计划被拒绝后的结案节点。
     */
    private Map<String, Object> closePlan(OverAllState state) {
        String planIdStr = state.value("planId", "unknown");
        String complaintIdStr = state.value("complaintId", "unknown");
        log.info("工作流[closePlan]：计划已拒绝关闭 planId={}，complaintId={}", planIdStr, complaintIdStr);
        return Map.of();
    }

    // ---- 工作流恢复辅助 ----

    private void resumeWorkflow(UUID planId, Map<String, Object> updates) {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(planId.toString())
                .build();
        try {

            // 1. 写入前读快照
            StateSnapshot before = compiledGraph.getState(config);
            log.info("【updateState前】planId={}, nextNode={}, state={}",
                    planId, before.config().nextNode(), before.state().data());

            RunnableConfig resumeConfig = compiledGraph.updateState(config, updates).withResume();

            // 2. 写入后再读一次，确认 Redis 里的值是否变了
            StateSnapshot after = compiledGraph.getState(resumeConfig);
            log.info("【updateState后】planId={}, checkpointId={}, nextNode={}, state={}",
                    planId, resumeConfig.checkPointId().orElse(null), after.config().nextNode(), after.state().data());

            compiledGraph.streamFromInitialNode(compiledGraph.cloneState(after.state().data()), resumeConfig).blockLast();
        } catch (Exception e) {
            log.error("工作流恢复失败：planId={}", planId, e);
            throw new KbException("工作流恢复失败：" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ---- 业务辅助方法 ----

    private Map<String, Object> decision(String party, String reason) {
        return Map.of("responsibleParty", party, "responsibilityReason", reason);
    }

    private ComplaintPlanningContext buildContextFromState(OverAllState state) {
        String orderId = state.value("orderId", "");
        String orderAmount = state.value("orderAmount", DEFAULT_ORDER_AMOUNT.toPlainString());
        String orderStatus = state.value("orderStatus", "COMPLETED");
        boolean delivered = Boolean.parseBoolean(state.value("logisticsDelivered", "false"));
        boolean interrupted = Boolean.parseBoolean(state.value("logisticsInterrupted", "false"));
        boolean within7Days = Boolean.parseBoolean(state.value("logisticsWithin7Days", "false"));

        String content = state.value("complaintContent", "");
        String normalized = content.toLowerCase();
        int violationCount = containsAny(normalized, "商家", "拒绝", "拖延") ? 2 : 0;
        int complaintCount = containsAny(normalized, "多次", "反复", "一直") ? 3 : 1;

        return new ComplaintPlanningContext(
                new ComplaintPlanningContext.ComplaintHistory(complaintCount, complaintCount > 1 ? 2 : 1),
                new ComplaintPlanningContext.OrderDetails(orderId, new BigDecimal(orderAmount),
                        "MERCHANT-001", "TRACK-" + orderId, orderStatus),
                new ComplaintPlanningContext.LogisticsTrace(delivered, interrupted, within7Days),
                new ComplaintPlanningContext.MerchantViolationRecord("MERCHANT-001", violationCount),
                new ComplaintPlanningContext.CompensationPolicy(MAX_REFUND_AMOUNT, MAX_COUPON_AMOUNT)
        );
    }

    private CompensationType compensationTypeFor(ResponsibleParty party) {
        return switch (party) {
            case MERCHANT -> CompensationType.REFUND_AND_COUPON;
            case LOGISTICS, PLATFORM -> CompensationType.REFUND;
            case DISPUTED -> CompensationType.NONE;
        };
    }

    private List<String> contactSequenceFor(ResponsibleParty party) {
        return switch (party) {
            case MERCHANT -> List.of("MERCHANT", "LOGISTICS");
            case LOGISTICS -> List.of("LOGISTICS", "MERCHANT");
            case PLATFORM -> List.of("PLATFORM");
            case DISPUTED -> List.of();
        };
    }

    private List<Object> buildFallbackPlan(ResponsibleParty primary) {
        if (primary == ResponsibleParty.PLATFORM) {
            return List.of(Map.of("action", "ESCALATE_TO_SENIOR",
                    "reason", "平台已为首要责任方，两次协商失败，升级高级专员介入"));
        }
        return List.of(
                Map.of(
                        "responsibleParty", ResponsibleParty.PLATFORM.name(),
                        "compensation", Map.of("amount", 200, "type", CompensationType.REFUND.name()),
                        "reason", "责任方拒绝承担或超时未响应时，平台先行兜底"
                ),
                Map.of("action", "ESCALATE_TO_SENIOR", "reason", "两次方案均失败，升级高级专员介入")
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new KbException("序列化 JSON 失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }
}
