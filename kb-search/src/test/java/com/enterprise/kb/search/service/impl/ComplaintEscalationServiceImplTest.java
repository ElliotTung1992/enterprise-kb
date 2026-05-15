package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.CompensationType;
import com.enterprise.kb.common.constants.ComplaintPlanStatus;
import com.enterprise.kb.common.constants.ComplaintStatus;
import com.enterprise.kb.common.constants.ResponsibleParty;
import com.enterprise.kb.common.exception.KbException;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.search.dto.ComplaintPlanModifyRequest;
import com.enterprise.kb.search.mapper.ComplaintMapper;
import com.enterprise.kb.search.mapper.ComplaintPlanMapper;
import com.enterprise.kb.search.model.Complaint;
import com.enterprise.kb.search.model.ComplaintPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintEscalationServiceImplTest {

    @Mock ComplaintMapper complaintMapper;
    @Mock ComplaintPlanMapper complaintPlanMapper;
    @InjectMocks ComplaintEscalationServiceImpl service;

    @Test
    void createComplaintAssignsOpenStatusAndCopiesFields() {
        UUID userId = UUID.randomUUID();
        ArgumentCaptor<Complaint> captor = ArgumentCaptor.forClass(Complaint.class);

        Complaint result = service.createComplaint(userId, "ORD-1001", "多次投诉未解决");

        verify(complaintMapper).insert(captor.capture());
        Complaint saved = captor.getValue();
        assertThat(result).isSameAs(saved);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getOrderId()).isEqualTo("ORD-1001");
        assertThat(saved.getContent()).isEqualTo("多次投诉未解决");
        assertThat(saved.getStatus()).isEqualTo(ComplaintStatus.OPEN);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void savePlanFillsDefaultsAndMovesComplaintToPendingReview() {
        UUID complaintId = UUID.randomUUID();
        ComplaintPlan plan = new ComplaintPlan();
        plan.setComplaintId(complaintId);
        plan.setResponsibleParty(ResponsibleParty.MERCHANT);
        plan.setResponsibilityReason("签收 7 天内质量问题，商家负责");

        ComplaintPlan result = service.savePlan(plan);

        ArgumentCaptor<ComplaintPlan> captor = ArgumentCaptor.forClass(ComplaintPlan.class);
        verify(complaintPlanMapper).insert(captor.capture());
        ComplaintPlan saved = captor.getValue();

        assertThat(result).isSameAs(saved);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCompensationType()).isEqualTo(CompensationType.NONE);
        assertThat(saved.getCompensationAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getContactSequence()).isEqualTo("[]");
        assertThat(saved.getTimeouts()).isEqualTo("{}");
        assertThat(saved.getFallbackPlan()).isEqualTo("[]");
        assertThat(saved.getStatus()).isEqualTo(ComplaintPlanStatus.PENDING_REVIEW);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        verify(complaintMapper).updateStatus(eq(complaintId), eq(ComplaintStatus.PENDING_REVIEW), any());
    }

    @Test
    void savePlanPreservesProvidedPlanFields() {
        UUID complaintId = UUID.randomUUID();
        ComplaintPlan plan = new ComplaintPlan();
        plan.setComplaintId(complaintId);
        plan.setResponsibleParty(ResponsibleParty.PLATFORM);
        plan.setResponsibilityReason("系统订单状态异常");
        plan.setCompensationType(CompensationType.REFUND_AND_COUPON);
        plan.setCompensationAmount(new BigDecimal("200.00"));
        plan.setContactSequence("[\"MERCHANT\",\"LOGISTICS\"]");
        plan.setTimeouts("{\"merchantResponseDeadline\":\"48h\"}");
        plan.setFallbackPlan("[{\"action\":\"ESCALATE_TO_SENIOR\"}]");
        plan.setStatus(ComplaintPlanStatus.APPROVED);

        ComplaintPlan saved = service.savePlan(plan);

        assertThat(saved.getCompensationType()).isEqualTo(CompensationType.REFUND_AND_COUPON);
        assertThat(saved.getCompensationAmount()).isEqualByComparingTo("200.00");
        assertThat(saved.getContactSequence()).isEqualTo("[\"MERCHANT\",\"LOGISTICS\"]");
        assertThat(saved.getTimeouts()).isEqualTo("{\"merchantResponseDeadline\":\"48h\"}");
        assertThat(saved.getFallbackPlan()).isEqualTo("[{\"action\":\"ESCALATE_TO_SENIOR\"}]");
        assertThat(saved.getStatus()).isEqualTo(ComplaintPlanStatus.APPROVED);
    }

    @Test
    void findComplaintByIdThrowsWhenNotExists() {
        UUID id = UUID.randomUUID();
        when(complaintMapper.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findComplaintById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findPlanByIdThrowsWhenNotExists() {
        UUID id = UUID.randomUUID();
        when(complaintPlanMapper.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findPlanById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listPlansDelegatesToMapper() {
        UUID complaintId = UUID.randomUUID();
        ComplaintPlan plan = new ComplaintPlan();
        plan.setComplaintId(complaintId);
        when(complaintPlanMapper.findByComplaintId(complaintId)).thenReturn(List.of(plan));

        List<ComplaintPlan> result = service.listPlans(complaintId);

        assertThat(result).containsExactly(plan);
    }

    // ---- Phase 3: HITL 审批 ----

    @Test
    void approvePlan_transitionsPendingReviewToApproved() {
        UUID planId = UUID.randomUUID();
        ComplaintPlan plan = pendingPlan(planId);
        when(complaintPlanMapper.findById(planId)).thenReturn(Optional.of(plan));

        ComplaintPlan result = service.approvePlan(planId, UUID.randomUUID(), null);

        verify(complaintPlanMapper).updateStatus(eq(planId), eq(ComplaintPlanStatus.APPROVED), any());
        assertThat(result.getStatus()).isEqualTo(ComplaintPlanStatus.APPROVED);
    }

    @Test
    void approvePlan_throwsWhenPlanIsNotPendingReview() {
        UUID planId = UUID.randomUUID();
        ComplaintPlan plan = pendingPlan(planId);
        plan.setStatus(ComplaintPlanStatus.APPROVED);
        when(complaintPlanMapper.findById(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.approvePlan(planId, UUID.randomUUID(), null))
                .isInstanceOf(KbException.class)
                .hasMessageContaining("PENDING_REVIEW");

        verify(complaintPlanMapper, never()).updateStatus(any(), eq(ComplaintPlanStatus.APPROVED), any());
    }

    @Test
    void rejectPlan_transitionsToPendingReviewToRejectedAndCloseComplaint() {
        UUID planId = UUID.randomUUID();
        UUID complaintId = UUID.randomUUID();
        ComplaintPlan plan = pendingPlan(planId);
        plan.setComplaintId(complaintId);
        when(complaintPlanMapper.findById(planId)).thenReturn(Optional.of(plan));

        ComplaintPlan result = service.rejectPlan(planId, UUID.randomUUID(), "审批不通过");

        verify(complaintPlanMapper).updateStatus(eq(planId), eq(ComplaintPlanStatus.REJECTED), any());
        verify(complaintMapper).updateStatus(eq(complaintId), eq(ComplaintStatus.CLOSED), any());
        assertThat(result.getStatus()).isEqualTo(ComplaintPlanStatus.REJECTED);
    }

    @Test
    void rejectPlan_throwsWhenPlanIsNotPendingReview() {
        UUID planId = UUID.randomUUID();
        ComplaintPlan plan = pendingPlan(planId);
        plan.setStatus(ComplaintPlanStatus.REJECTED);
        when(complaintPlanMapper.findById(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.rejectPlan(planId, UUID.randomUUID(), null))
                .isInstanceOf(KbException.class)
                .hasMessageContaining("PENDING_REVIEW");
    }

    @Test
    void modifyAndApprovePlan_appliesNonNullFieldsAndTransitionsToApproved() {
        UUID planId = UUID.randomUUID();
        ComplaintPlan plan = pendingPlan(planId);
        plan.setResponsibleParty(ResponsibleParty.MERCHANT);
        plan.setCompensationType(CompensationType.NONE);
        plan.setCompensationAmount(BigDecimal.ZERO);
        when(complaintPlanMapper.findById(planId)).thenReturn(Optional.of(plan));

        ComplaintPlanModifyRequest req = new ComplaintPlanModifyRequest(
                ResponsibleParty.PLATFORM,
                CompensationType.REFUND,
                new BigDecimal("300"),
                "平台承担"
        );

        ComplaintPlan result = service.modifyAndApprovePlan(planId, UUID.randomUUID(), req);

        verify(complaintPlanMapper).updateFields(
                eq(planId),
                eq(ResponsibleParty.PLATFORM),
                eq(CompensationType.REFUND),
                eq(new BigDecimal("300")),
                any());
        verify(complaintPlanMapper).updateStatus(eq(planId), eq(ComplaintPlanStatus.APPROVED), any());
        assertThat(result.getStatus()).isEqualTo(ComplaintPlanStatus.APPROVED);
        assertThat(result.getResponsibleParty()).isEqualTo(ResponsibleParty.PLATFORM);
        assertThat(result.getCompensationType()).isEqualTo(CompensationType.REFUND);
        assertThat(result.getCompensationAmount()).isEqualByComparingTo("300");
    }

    @Test
    void modifyAndApprovePlan_nullFieldsPassedThroughToMapperButNotAppliedToReturnedPlan() {
        UUID planId = UUID.randomUUID();
        ComplaintPlan plan = pendingPlan(planId);
        plan.setResponsibleParty(ResponsibleParty.MERCHANT);
        plan.setCompensationType(CompensationType.COUPON);
        when(complaintPlanMapper.findById(planId)).thenReturn(Optional.of(plan));

        ComplaintPlanModifyRequest req = new ComplaintPlanModifyRequest(null, null, null, "只改备注");

        ComplaintPlan result = service.modifyAndApprovePlan(planId, UUID.randomUUID(), req);

        verify(complaintPlanMapper).updateFields(eq(planId), isNull(), isNull(), isNull(), any());
        assertThat(result.getResponsibleParty()).isEqualTo(ResponsibleParty.MERCHANT);
        assertThat(result.getCompensationType()).isEqualTo(CompensationType.COUPON);
    }

    @Test
    void modifyAndApprovePlan_throwsWhenPlanIsNotPendingReview() {
        UUID planId = UUID.randomUUID();
        ComplaintPlan plan = pendingPlan(planId);
        plan.setStatus(ComplaintPlanStatus.EXECUTING);
        when(complaintPlanMapper.findById(planId)).thenReturn(Optional.of(plan));

        ComplaintPlanModifyRequest req = new ComplaintPlanModifyRequest(null, null, null, null);

        assertThatThrownBy(() -> service.modifyAndApprovePlan(planId, UUID.randomUUID(), req))
                .isInstanceOf(KbException.class)
                .hasMessageContaining("PENDING_REVIEW");
    }

    private ComplaintPlan pendingPlan(UUID planId) {
        ComplaintPlan plan = new ComplaintPlan();
        plan.setId(planId);
        plan.setComplaintId(UUID.randomUUID());
        plan.setStatus(ComplaintPlanStatus.PENDING_REVIEW);
        return plan;
    }
}
