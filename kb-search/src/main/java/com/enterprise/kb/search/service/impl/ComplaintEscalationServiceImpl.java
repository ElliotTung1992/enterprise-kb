package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.CompensationType;
import com.enterprise.kb.common.constants.ComplaintPlanStatus;
import com.enterprise.kb.common.constants.ComplaintStatus;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.search.mapper.ComplaintMapper;
import com.enterprise.kb.search.mapper.ComplaintPlanMapper;
import com.enterprise.kb.search.model.Complaint;
import com.enterprise.kb.search.model.ComplaintPlan;
import com.enterprise.kb.search.service.ComplaintEscalationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 投诉升级基础数据服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintEscalationServiceImpl implements ComplaintEscalationService {

    private static final String EMPTY_JSON_ARRAY = "[]";
    private static final String EMPTY_JSON_OBJECT = "{}";

    private final ComplaintMapper complaintMapper;
    private final ComplaintPlanMapper complaintPlanMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Complaint createComplaint(UUID userId, String orderId, String content) {
        Instant now = Instant.now();
        Complaint complaint = new Complaint();
        complaint.setId(UUID.randomUUID());
        complaint.setUserId(userId);
        complaint.setOrderId(orderId);
        complaint.setContent(content);
        complaint.setStatus(ComplaintStatus.OPEN);
        complaint.setCreatedAt(now);
        complaint.setUpdatedAt(now);
        complaintMapper.insert(complaint);
        log.info("创建投诉升级案件：id={}，orderId={}", complaint.getId(), orderId);
        return complaint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Complaint findComplaintById(UUID id) {
        return complaintMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint", id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ComplaintPlan savePlan(ComplaintPlan plan) {
        Instant now = Instant.now();
        if (plan.getId() == null) {
            plan.setId(UUID.randomUUID());
        }
        if (plan.getCompensationType() == null) {
            plan.setCompensationType(CompensationType.NONE);
        }
        if (plan.getCompensationAmount() == null) {
            plan.setCompensationAmount(BigDecimal.ZERO);
        }
        if (plan.getContactSequence() == null || plan.getContactSequence().isBlank()) {
            plan.setContactSequence(EMPTY_JSON_ARRAY);
        }
        if (plan.getTimeouts() == null || plan.getTimeouts().isBlank()) {
            plan.setTimeouts(EMPTY_JSON_OBJECT);
        }
        if (plan.getFallbackPlan() == null || plan.getFallbackPlan().isBlank()) {
            plan.setFallbackPlan(EMPTY_JSON_ARRAY);
        }
        if (plan.getStatus() == null) {
            plan.setStatus(ComplaintPlanStatus.PENDING_REVIEW);
        }
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        complaintPlanMapper.insert(plan);
        complaintMapper.updateStatus(plan.getComplaintId(), ComplaintStatus.PENDING_REVIEW, now);
        log.info("保存投诉处理计划：id={}，complaintId={}", plan.getId(), plan.getComplaintId());
        return plan;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public ComplaintPlan findPlanById(UUID id) {
        return complaintPlanMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ComplaintPlan", id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<ComplaintPlan> listPlans(UUID complaintId) {
        return complaintPlanMapper.findByComplaintId(complaintId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateComplaintStatus(UUID id, ComplaintStatus status) {
        complaintMapper.updateStatus(id, status, Instant.now());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updatePlanStatus(UUID id, ComplaintPlanStatus status) {
        complaintPlanMapper.updateStatus(id, status, Instant.now());
    }
}
