package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.CompensationType;
import com.enterprise.kb.common.constants.ComplaintPlanStatus;
import com.enterprise.kb.common.constants.ResponsibleParty;
import com.enterprise.kb.common.exception.KbException;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.model.ComplaintPlan;
import com.enterprise.kb.search.service.ComplaintEscalationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintExecutorServiceImplTest {

    @Mock ComplaintEscalationService complaintEscalationService;
    @Mock ModelProviderResolver modelProviderResolver;
    @InjectMocks ComplaintExecutorServiceImpl service;

    // ---- Slice 1: guard ----

    @Test
    void execute_throwsWhenPlanIsNotApproved() {
        UUID planId = UUID.randomUUID();
        ComplaintPlan plan = plan(planId, UUID.randomUUID(), ComplaintPlanStatus.PENDING_REVIEW);
        when(complaintEscalationService.findPlanById(planId)).thenReturn(plan);

        assertThatThrownBy(() -> service.execute(planId))
                .isInstanceOf(KbException.class)
                .hasMessageContaining("APPROVED");

        verify(complaintEscalationService, never()).startPlanExecution(any(), any());
    }

    // ---- Slice 2: failure path → plan FAILED ----

    @Test
    void execute_marksFailedWhenAgentThrows() {
        UUID planId = UUID.randomUUID();
        UUID complaintId = UUID.randomUUID();
        ComplaintPlan plan = plan(planId, complaintId, ComplaintPlanStatus.APPROVED);
        when(complaintEscalationService.findPlanById(planId)).thenReturn(plan);
        when(modelProviderResolver.resolveChatClient(null))
                .thenThrow(new KbException("AI 服务不可用", HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> service.execute(planId))
                .isInstanceOf(KbException.class)
                .hasMessageContaining("计划执行失败");

        verify(complaintEscalationService).startPlanExecution(eq(planId), any());
        verify(complaintEscalationService).updatePlanStatus(planId, ComplaintPlanStatus.FAILED);
        verify(complaintEscalationService, never()).updatePlanStatus(planId, ComplaintPlanStatus.COMPLETED);
        verify(complaintEscalationService, never()).updateComplaintStatus(any(), any());
    }

    private ComplaintPlan plan(UUID planId, UUID complaintId, ComplaintPlanStatus status) {
        ComplaintPlan plan = new ComplaintPlan();
        plan.setId(planId);
        plan.setComplaintId(complaintId);
        plan.setStatus(status);
        plan.setResponsibleParty(ResponsibleParty.MERCHANT);
        plan.setResponsibilityReason("测试原因");
        plan.setCompensationType(CompensationType.REFUND);
        plan.setCompensationAmount(new BigDecimal("100.00"));
        plan.setContactSequence("[\"MERCHANT\"]");
        return plan;
    }
}
