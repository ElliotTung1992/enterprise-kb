package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.CompensationType;
import com.enterprise.kb.common.constants.ComplaintPlanStatus;
import com.enterprise.kb.common.constants.ComplaintStatus;
import com.enterprise.kb.common.constants.ResponsibleParty;
import com.enterprise.kb.common.exception.KbException;
import com.enterprise.kb.search.dto.ComplaintPlanningContext;
import com.enterprise.kb.search.dto.ComplaintResponsibilityDecision;
import com.enterprise.kb.search.model.Complaint;
import com.enterprise.kb.search.model.ComplaintPlan;
import com.enterprise.kb.search.service.ComplaintEscalationService;
import com.enterprise.kb.search.service.ComplaintPlannerService;
import com.enterprise.kb.search.service.ComplaintResponsibilityInferenceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 投诉升级 Planner MVP 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintPlannerServiceImpl implements ComplaintPlannerService {

    private static final BigDecimal DEFAULT_ORDER_AMOUNT = new BigDecimal("299.00");
    private static final BigDecimal MAX_REFUND_AMOUNT = new BigDecimal("500.00");
    private static final BigDecimal MAX_COUPON_AMOUNT = new BigDecimal("50.00");

    private final ComplaintEscalationService complaintEscalationService;
    private final ComplaintResponsibilityInferenceService responsibilityInferenceService;
    private final ObjectMapper objectMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ComplaintPlan generatePlan(UUID complaintId) {
        Complaint complaint = complaintEscalationService.findComplaintById(complaintId);
        complaintEscalationService.updateComplaintStatus(complaintId, ComplaintStatus.PLANNING);

        ComplaintPlanningContext context = collectPlanningContext(complaint);
        ComplaintResponsibilityDecision decision = determineResponsibility(complaint, context);

        ComplaintPlan plan = buildPlan(complaint, context, decision);
        ComplaintPlan savedPlan = complaintEscalationService.savePlan(plan);
        log.info("生成投诉升级处理计划：complaintId={}，planId={}，responsibleParty={}",
                complaintId, savedPlan.getId(), savedPlan.getResponsibleParty());
        return savedPlan;
    }

    private ComplaintPlanningContext collectPlanningContext(Complaint complaint) {
        ComplaintPlanningContext.OrderDetails orderDetails = getOrderDetails(complaint.getOrderId(), complaint.getContent());
        return new ComplaintPlanningContext(
                getComplaintHistory(complaint.getContent()),
                orderDetails,
                getLogisticsTrace(complaint.getContent()),
                getMerchantViolationRecord(orderDetails.merchantId(), complaint.getContent()),
                getCompensationPolicy()
        );
    }

    private ComplaintResponsibilityDecision determineResponsibility(Complaint complaint,
                                                                     ComplaintPlanningContext context) {
        String content = normalize(complaint.getContent());
        ComplaintPlanningContext.OrderDetails order = context.orderDetails();
        ComplaintPlanningContext.LogisticsTrace logistics = context.logisticsTrace();

        if (logistics.delivered() && containsAny(content, "损坏", "破损")) {
            return new ComplaintResponsibilityDecision(ResponsibleParty.MERCHANT,
                    "物流签收后投诉商品损坏，按规则由商家承担责任。");
        }
        if (logistics.interruptedOver48h()) {
            return new ComplaintResponsibilityDecision(ResponsibleParty.LOGISTICS,
                    "物流轨迹中断超过 48 小时，按规则由物流承担责任。");
        }
        if ("SYSTEM_ABNORMAL".equals(order.status())) {
            return new ComplaintResponsibilityDecision(ResponsibleParty.PLATFORM,
                    "系统订单状态异常，按规则由平台承担责任。");
        }
        if (logistics.deliveredWithin7Days() && containsAny(content, "质量", "故障", "坏了", "无法使用")) {
            return new ComplaintResponsibilityDecision(ResponsibleParty.MERCHANT,
                    "签收 7 天内反馈质量问题，按规则由商家承担责任。");
        }
        if ("NOT_SHIPPED_OVERDUE".equals(order.status())) {
            return new ComplaintResponsibilityDecision(ResponsibleParty.MERCHANT,
                    "订单未发货且已超时，按规则由商家承担责任。");
        }
        return responsibilityInferenceService.infer(complaint, context);
    }

    private ComplaintPlan buildPlan(Complaint complaint,
                                    ComplaintPlanningContext context,
                                    ComplaintResponsibilityDecision decision) {
        ComplaintPlan plan = new ComplaintPlan();
        plan.setComplaintId(complaint.getId());
        plan.setResponsibleParty(decision.responsibleParty());
        plan.setResponsibilityReason(decision.responsibilityReason());
        plan.setCompensationType(compensationTypeFor(decision.responsibleParty()));
        plan.setCompensationAmount(compensationAmountFor(context, decision.responsibleParty()));
        plan.setContactSequence(toJson(contactSequenceFor(decision.responsibleParty())));
        plan.setTimeouts(toJson(Map.of(
                "merchantResponseDeadline", "48h",
                "onTimeout", "TRIGGER_REPLANNER"
        )));
        plan.setFallbackPlan(toJson(buildFallbackPlan(decision.responsibleParty())));
        plan.setReplanCount(0);
        plan.setStatus(ComplaintPlanStatus.PENDING_REVIEW);
        return plan;
    }

    private ComplaintPlanningContext.ComplaintHistory getComplaintHistory(String content) {
        int count = containsAny(normalize(content), "多次", "反复", "一直") ? 3 : 1;
        int unresolved = count > 1 ? 2 : 1;
        return new ComplaintPlanningContext.ComplaintHistory(count, unresolved);
    }

    private ComplaintPlanningContext.OrderDetails getOrderDetails(String orderId, String content) {
        String normalized = normalize(content);
        String status = "COMPLETED";
        if (containsAny(normalized, "系统", "状态异常")) {
            status = "SYSTEM_ABNORMAL";
        } else if (containsAny(normalized, "未发货", "不发货")) {
            status = "NOT_SHIPPED_OVERDUE";
        }
        return new ComplaintPlanningContext.OrderDetails(
                orderId,
                DEFAULT_ORDER_AMOUNT,
                "MERCHANT-001",
                "TRACK-" + orderId,
                status
        );
    }

    private ComplaintPlanningContext.LogisticsTrace getLogisticsTrace(String content) {
        String normalized = normalize(content);
        boolean interrupted = containsAny(normalized, "物流中断", "轨迹中断", "48小时", "48h");
        boolean delivered = containsAny(normalized, "签收", "收到", "到货", "损坏", "破损", "质量");
        boolean deliveredWithin7Days = delivered && !containsAny(normalized, "超过7天", "超7天");
        return new ComplaintPlanningContext.LogisticsTrace(delivered, interrupted, deliveredWithin7Days);
    }

    private ComplaintPlanningContext.MerchantViolationRecord getMerchantViolationRecord(String merchantId,
                                                                                        String content) {
        int violationCount = containsAny(normalize(content), "商家", "拒绝", "拖延") ? 2 : 0;
        return new ComplaintPlanningContext.MerchantViolationRecord(merchantId, violationCount);
    }

    private ComplaintPlanningContext.CompensationPolicy getCompensationPolicy() {
        return new ComplaintPlanningContext.CompensationPolicy(MAX_REFUND_AMOUNT, MAX_COUPON_AMOUNT);
    }

    private CompensationType compensationTypeFor(ResponsibleParty responsibleParty) {
        return switch (responsibleParty) {
            case MERCHANT -> CompensationType.REFUND_AND_COUPON;
            case LOGISTICS, PLATFORM -> CompensationType.REFUND;
            case DISPUTED -> CompensationType.NONE;
        };
    }

    private BigDecimal compensationAmountFor(ComplaintPlanningContext context, ResponsibleParty responsibleParty) {
        if (responsibleParty == ResponsibleParty.DISPUTED) {
            return BigDecimal.ZERO;
        }
        BigDecimal orderAmount = context.orderDetails().amount();
        return orderAmount.min(context.compensationPolicy().maxRefundAmount());
    }

    private List<String> contactSequenceFor(ResponsibleParty responsibleParty) {
        return switch (responsibleParty) {
            case MERCHANT -> List.of("MERCHANT", "LOGISTICS");
            case LOGISTICS -> List.of("LOGISTICS", "MERCHANT");
            case PLATFORM -> List.of("PLATFORM");
            case DISPUTED -> List.of();
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new KbException("生成投诉计划 JSON 失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private List<Object> buildFallbackPlan(ResponsibleParty primary) {
        if (primary == ResponsibleParty.PLATFORM) {
            // PLATFORM is already the primary; a PLATFORM fallback would be circular
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

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase();
    }
}
