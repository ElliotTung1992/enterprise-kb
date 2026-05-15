package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.CompensationType;
import com.enterprise.kb.common.constants.ComplaintPlanStatus;
import com.enterprise.kb.common.constants.ComplaintStatus;
import com.enterprise.kb.common.constants.ResponsibleParty;
import com.enterprise.kb.common.exception.KbException;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.search.mapper.ComplaintPlanMapper;
import com.enterprise.kb.search.model.ComplaintPlan;
import com.enterprise.kb.search.service.ComplaintEscalationService;
import com.enterprise.kb.search.service.ComplaintReplannerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 投诉升级重规划服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintReplannerServiceImpl implements ComplaintReplannerService {

    private static final int MAX_REPLAN_COUNT = 2;
    private static final String ACTION_ESCALATE = "ESCALATE_TO_SENIOR";

    private final ComplaintEscalationService complaintEscalationService;
    private final ComplaintPlanMapper complaintPlanMapper;
    private final ObjectMapper objectMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void replan(UUID planId) {
        ComplaintPlan current = complaintPlanMapper.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("ComplaintPlan", planId));

        List<Map<String, Object>> fallbacks = parseFallback(current.getFallbackPlan());
        int idx = current.getReplanCount();

        if (idx >= MAX_REPLAN_COUNT || idx >= fallbacks.size()) {
            escalate(current, "已达最大重规划次数（" + MAX_REPLAN_COUNT + "）或无可用 fallback");
            return;
        }

        Map<String, Object> entry = fallbacks.get(idx);
        if (ACTION_ESCALATE.equals(entry.get("action"))) {
            escalate(current, (String) entry.getOrDefault("reason", "fallback 指定升级高级专员"));
            return;
        }

        createReplan(current, entry);
    }

    private void createReplan(ComplaintPlan current, Map<String, Object> entry) {
        ResponsibleParty newParty = ResponsibleParty.valueOf((String) entry.get("responsibleParty"));

        @SuppressWarnings("unchecked")
        Map<String, Object> comp = (Map<String, Object>) entry.get("compensation");
        CompensationType newType = CompensationType.valueOf((String) comp.get("type"));
        BigDecimal newAmount = new BigDecimal(comp.get("amount").toString());
        String reason = (String) entry.getOrDefault("reason", "自动重规划：取 fallback 方案");

        ComplaintPlan newPlan = new ComplaintPlan();
        newPlan.setComplaintId(current.getComplaintId());
        newPlan.setResponsibleParty(newParty);
        newPlan.setResponsibilityReason(reason);
        newPlan.setCompensationType(newType);
        newPlan.setCompensationAmount(newAmount);
        newPlan.setContactSequence(current.getContactSequence());
        newPlan.setTimeouts(current.getTimeouts());
        newPlan.setFallbackPlan(current.getFallbackPlan());
        newPlan.setReplanCount(current.getReplanCount() + 1);
        newPlan.setStatus(ComplaintPlanStatus.PENDING_REVIEW);

        complaintEscalationService.savePlan(newPlan);

        complaintPlanMapper.updateStatus(current.getId(), ComplaintPlanStatus.FAILED, Instant.now());

        log.info("重规划完成：旧 planId={}，新 planId={}，新责任方={}，replanCount={}",
                current.getId(), newPlan.getId(), newParty, newPlan.getReplanCount());
    }

    private void escalate(ComplaintPlan plan, String reason) {
        complaintEscalationService.updateComplaintStatus(plan.getComplaintId(), ComplaintStatus.ESCALATED);
        complaintPlanMapper.updateStatus(plan.getId(), ComplaintPlanStatus.FAILED, Instant.now());
        log.warn("投诉升级至高级专员：complaintId={}，planId={}，原因：{}",
                plan.getComplaintId(), plan.getId(), reason);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseFallback(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new KbException("解析 fallback 计划失败：" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
