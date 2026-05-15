package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.CompensationType;
import com.enterprise.kb.common.constants.ComplaintPlanStatus;
import com.enterprise.kb.common.constants.ComplaintStatus;
import com.enterprise.kb.common.constants.ResponsibleParty;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.search.mapper.ComplaintPlanMapper;
import com.enterprise.kb.search.model.ComplaintPlan;
import com.enterprise.kb.search.service.ComplaintEscalationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintReplannerServiceImplTest {

    @Mock
    ComplaintEscalationService complaintEscalationService;
    @Mock
    ComplaintPlanMapper complaintPlanMapper;
    @Spy
    ObjectMapper objectMapper;
    @InjectMocks
    ComplaintReplannerServiceImpl service;

    // ---- Slice 1: 正常重规划 ----

    @Test
    void replan_withFirstFallback_createsNewPlanWithFallbackDataAndMarksOldFailed() {
        UUID planId = UUID.randomUUID();
        UUID complaintId = UUID.randomUUID();
        ComplaintPlan current = executingPlan(planId, complaintId, 0, """
                [{"responsibleParty":"PLATFORM","compensation":{"amount":200,"type":"REFUND"},"reason":"平台垫付"}]
                """);
        when(complaintPlanMapper.findById(planId)).thenReturn(Optional.of(current));

        service.replan(planId);

        ArgumentCaptor<ComplaintPlan> newPlanCaptor = ArgumentCaptor.forClass(ComplaintPlan.class);
        verify(complaintEscalationService).savePlan(newPlanCaptor.capture());
        ComplaintPlan newPlan = newPlanCaptor.getValue();

        assertThat(newPlan.getComplaintId()).isEqualTo(complaintId);
        assertThat(newPlan.getResponsibleParty()).isEqualTo(ResponsibleParty.PLATFORM);
        assertThat(newPlan.getCompensationType()).isEqualTo(CompensationType.REFUND);
        assertThat(newPlan.getCompensationAmount()).isEqualByComparingTo("200");
        assertThat(newPlan.getResponsibilityReason()).isEqualTo("平台垫付");
        assertThat(newPlan.getReplanCount()).isEqualTo(1);

        verify(complaintPlanMapper).updateStatus(eq(planId), eq(ComplaintPlanStatus.FAILED), any());
    }

    @Test
    void replan_newPlanInheritsContactSequenceTimeoutsAndFallbackFromCurrentPlan() {
        UUID planId = UUID.randomUUID();
        String contactSeq = "[\"MERCHANT\",\"LOGISTICS\"]";
        String timeouts = "{\"merchantResponseDeadline\":\"48h\"}";
        String fallback = "[{\"responsibleParty\":\"LOGISTICS\",\"compensation\":{\"amount\":100,\"type\":\"REFUND\"},\"reason\":\"r\"}]";
        ComplaintPlan current = executingPlan(planId, UUID.randomUUID(), 0, fallback);
        current.setContactSequence(contactSeq);
        current.setTimeouts(timeouts);
        when(complaintPlanMapper.findById(planId)).thenReturn(Optional.of(current));

        service.replan(planId);

        ArgumentCaptor<ComplaintPlan> captor = ArgumentCaptor.forClass(ComplaintPlan.class);
        verify(complaintEscalationService).savePlan(captor.capture());
        ComplaintPlan newPlan = captor.getValue();

        assertThat(newPlan.getContactSequence()).isEqualTo(contactSeq);
        assertThat(newPlan.getTimeouts()).isEqualTo(timeouts);
        assertThat(newPlan.getFallbackPlan()).isEqualTo(fallback);
    }

    // ---- Slice 2: 达到最大重规划次数 → 升级 ----

    @Test
    void replan_whenReplanCountReachesMax_escalatesComplaint() {
        UUID planId = UUID.randomUUID();
        UUID complaintId = UUID.randomUUID();
        ComplaintPlan current = executingPlan(planId, complaintId, 2,
                "[{\"responsibleParty\":\"PLATFORM\",\"compensation\":{\"amount\":100,\"type\":\"REFUND\"},\"reason\":\"r\"}]");
        when(complaintPlanMapper.findById(planId)).thenReturn(Optional.of(current));

        service.replan(planId);

        verify(complaintEscalationService).updateComplaintStatus(eq(complaintId), eq(ComplaintStatus.ESCALATED));
        verify(complaintPlanMapper).updateStatus(eq(planId), eq(ComplaintPlanStatus.FAILED), any());
        verify(complaintEscalationService, never()).savePlan(any());
    }

    // ---- Slice 3: fallback 条目为 ESCALATE_TO_SENIOR → 升级 ----

    @Test
    void replan_whenFallbackEntryIsEscalateToSenior_escalatesComplaint() {
        UUID planId = UUID.randomUUID();
        UUID complaintId = UUID.randomUUID();
        ComplaintPlan current = executingPlan(planId, complaintId, 0,
                "[{\"action\":\"ESCALATE_TO_SENIOR\",\"reason\":\"两次方案均失败\"}]");
        when(complaintPlanMapper.findById(planId)).thenReturn(Optional.of(current));

        service.replan(planId);

        verify(complaintEscalationService).updateComplaintStatus(eq(complaintId), eq(ComplaintStatus.ESCALATED));
        verify(complaintPlanMapper).updateStatus(eq(planId), eq(ComplaintPlanStatus.FAILED), any());
        verify(complaintEscalationService, never()).savePlan(any());
    }

    // ---- Slice 4: fallback 为空数组 → 升级 ----

    @Test
    void replan_whenFallbackIsEmpty_escalatesComplaint() {
        UUID planId = UUID.randomUUID();
        UUID complaintId = UUID.randomUUID();
        ComplaintPlan current = executingPlan(planId, complaintId, 0, "[]");
        when(complaintPlanMapper.findById(planId)).thenReturn(Optional.of(current));

        service.replan(planId);

        verify(complaintEscalationService).updateComplaintStatus(eq(complaintId), eq(ComplaintStatus.ESCALATED));
        verify(complaintEscalationService, never()).savePlan(any());
    }

    // ---- Slice 5: planId 不存在 → 抛异常 ----

    @Test
    void replan_throwsWhenPlanNotFound() {
        UUID planId = UUID.randomUUID();
        when(complaintPlanMapper.findById(planId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replan(planId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- Slice 6: 第二次重规划使用 fallback[1] ----

    @Test
    void replan_secondReplanUsesFallbackAtIndex1() {
        UUID planId = UUID.randomUUID();
        UUID complaintId = UUID.randomUUID();
        ComplaintPlan current = executingPlan(planId, complaintId, 1, """
                [
                  {"responsibleParty":"PLATFORM","compensation":{"amount":200,"type":"REFUND"},"reason":"第一备选"},
                  {"responsibleParty":"LOGISTICS","compensation":{"amount":150,"type":"COUPON"},"reason":"第二备选"}
                ]
                """);
        when(complaintPlanMapper.findById(planId)).thenReturn(Optional.of(current));

        service.replan(planId);

        ArgumentCaptor<ComplaintPlan> captor = ArgumentCaptor.forClass(ComplaintPlan.class);
        verify(complaintEscalationService).savePlan(captor.capture());
        ComplaintPlan newPlan = captor.getValue();

        assertThat(newPlan.getResponsibleParty()).isEqualTo(ResponsibleParty.LOGISTICS);
        assertThat(newPlan.getCompensationType()).isEqualTo(CompensationType.COUPON);
        assertThat(newPlan.getCompensationAmount()).isEqualByComparingTo("150");
        assertThat(newPlan.getReplanCount()).isEqualTo(2);
    }

    private ComplaintPlan executingPlan(UUID planId, UUID complaintId, int replanCount, String fallbackPlan) {
        ComplaintPlan plan = new ComplaintPlan();
        plan.setId(planId);
        plan.setComplaintId(complaintId);
        plan.setStatus(ComplaintPlanStatus.EXECUTING);
        plan.setReplanCount(replanCount);
        plan.setFallbackPlan(fallbackPlan);
        plan.setContactSequence("[]");
        plan.setTimeouts("{}");
        plan.setResponsibleParty(ResponsibleParty.MERCHANT);
        plan.setCompensationType(CompensationType.REFUND);
        plan.setCompensationAmount(BigDecimal.TEN);
        return plan;
    }
}
