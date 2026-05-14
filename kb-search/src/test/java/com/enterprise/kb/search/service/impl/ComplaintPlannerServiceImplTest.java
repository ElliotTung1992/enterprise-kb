package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.CompensationType;
import com.enterprise.kb.common.constants.ComplaintPlanStatus;
import com.enterprise.kb.common.constants.ComplaintStatus;
import com.enterprise.kb.common.constants.ResponsibleParty;
import com.enterprise.kb.search.dto.ComplaintPlanningContext;
import com.enterprise.kb.search.dto.ComplaintResponsibilityDecision;
import com.enterprise.kb.search.model.Complaint;
import com.enterprise.kb.search.model.ComplaintPlan;
import com.enterprise.kb.search.service.ComplaintEscalationService;
import com.enterprise.kb.search.service.ComplaintResponsibilityInferenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintPlannerServiceImplTest {

    @Mock ComplaintEscalationService complaintEscalationService;
    @Mock ComplaintResponsibilityInferenceService responsibilityInferenceService;

    ComplaintPlannerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ComplaintPlannerServiceImpl(
                complaintEscalationService,
                responsibilityInferenceService,
                new ObjectMapper()
        );
    }

    @Test
    void generatePlanUsesMerchantRuleForDeliveredDamagedGoods() {
        UUID complaintId = UUID.randomUUID();
        Complaint complaint = complaint(complaintId, "ORD-1001", "商品签收后发现破损，多次投诉未解决");
        when(complaintEscalationService.findComplaintById(complaintId)).thenReturn(complaint);
        when(complaintEscalationService.savePlan(any())).thenAnswer(inv -> inv.getArgument(0));

        ComplaintPlan plan = service.generatePlan(complaintId);

        assertThat(plan.getResponsibleParty()).isEqualTo(ResponsibleParty.MERCHANT);
        assertThat(plan.getResponsibilityReason()).contains("商品损坏");
        assertThat(plan.getCompensationType()).isEqualTo(CompensationType.REFUND_AND_COUPON);
        assertThat(plan.getCompensationAmount()).isEqualByComparingTo(new BigDecimal("299.00"));
        assertThat(plan.getContactSequence()).contains("MERCHANT", "LOGISTICS");
        assertThat(plan.getTimeouts()).contains("48h", "TRIGGER_REPLANNER");
        assertThat(plan.getFallbackPlan()).contains("ESCALATE_TO_SENIOR");
        assertThat(plan.getStatus()).isEqualTo(ComplaintPlanStatus.PENDING_REVIEW);
        verify(complaintEscalationService).updateComplaintStatus(complaintId, ComplaintStatus.PLANNING);
        verify(complaintEscalationService).savePlan(any(ComplaintPlan.class));
        verify(responsibilityInferenceService, never()).infer(any(), any());
    }

    @Test
    void generatePlanUsesLogisticsRuleForInterruptedTrace() {
        UUID complaintId = UUID.randomUUID();
        Complaint complaint = complaint(complaintId, "ORD-1002", "物流轨迹中断超过48小时，用户一直没有收到货");
        when(complaintEscalationService.findComplaintById(complaintId)).thenReturn(complaint);
        when(complaintEscalationService.savePlan(any())).thenAnswer(inv -> inv.getArgument(0));

        ComplaintPlan plan = service.generatePlan(complaintId);

        assertThat(plan.getResponsibleParty()).isEqualTo(ResponsibleParty.LOGISTICS);
        assertThat(plan.getResponsibilityReason()).contains("物流轨迹中断超过 48 小时");
        assertThat(plan.getCompensationType()).isEqualTo(CompensationType.REFUND);
        assertThat(plan.getContactSequence()).contains("LOGISTICS", "MERCHANT");
    }

    @Test
    void generatePlanUsesLlmFallbackForBoundaryCase() {
        UUID complaintId = UUID.randomUUID();
        Complaint complaint = complaint(complaintId, "ORD-1003", "客服和商家说法不一致，用户要求平台介入");
        when(complaintEscalationService.findComplaintById(complaintId)).thenReturn(complaint);
        when(responsibilityInferenceService.infer(any(), any()))
                .thenReturn(new ComplaintResponsibilityDecision(ResponsibleParty.PLATFORM, "多方说法冲突，平台需介入兜底。"));
        when(complaintEscalationService.savePlan(any())).thenAnswer(inv -> inv.getArgument(0));

        ComplaintPlan plan = service.generatePlan(complaintId);

        assertThat(plan.getResponsibleParty()).isEqualTo(ResponsibleParty.PLATFORM);
        assertThat(plan.getResponsibilityReason()).contains("平台需介入");
        assertThat(plan.getCompensationType()).isEqualTo(CompensationType.REFUND);
        verify(responsibilityInferenceService).infer(any(Complaint.class), any(ComplaintPlanningContext.class));
    }

    @Test
    void generatePlanPersistsDisputedWhenLlmIsUncertain() {
        UUID complaintId = UUID.randomUUID();
        Complaint complaint = complaint(complaintId, "ORD-1004", "情况比较复杂，希望有人帮忙判断");
        when(complaintEscalationService.findComplaintById(complaintId)).thenReturn(complaint);
        when(responsibilityInferenceService.infer(any(), any()))
                .thenReturn(new ComplaintResponsibilityDecision(ResponsibleParty.DISPUTED, "证据不足，需人工判定。"));
        when(complaintEscalationService.savePlan(any())).thenAnswer(inv -> inv.getArgument(0));

        ComplaintPlan plan = service.generatePlan(complaintId);

        assertThat(plan.getResponsibleParty()).isEqualTo(ResponsibleParty.DISPUTED);
        assertThat(plan.getCompensationType()).isEqualTo(CompensationType.NONE);
        assertThat(plan.getCompensationAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(plan.getContactSequence()).isEqualTo("[]");
        assertThat(plan.getStatus()).isEqualTo(ComplaintPlanStatus.PENDING_REVIEW);
    }

    @Test
    void generatePlanSavesPlanForComplaint() {
        UUID complaintId = UUID.randomUUID();
        Complaint complaint = complaint(complaintId, "ORD-1005", "未发货超时，多次联系商家无果");
        when(complaintEscalationService.findComplaintById(complaintId)).thenReturn(complaint);
        when(complaintEscalationService.savePlan(any())).thenAnswer(inv -> inv.getArgument(0));

        service.generatePlan(complaintId);

        ArgumentCaptor<ComplaintPlan> captor = ArgumentCaptor.forClass(ComplaintPlan.class);
        verify(complaintEscalationService).savePlan(captor.capture());
        ComplaintPlan saved = captor.getValue();
        assertThat(saved.getComplaintId()).isEqualTo(complaintId);
        assertThat(saved.getResponsibleParty()).isEqualTo(ResponsibleParty.MERCHANT);
        assertThat(saved.getStatus()).isEqualTo(ComplaintPlanStatus.PENDING_REVIEW);
    }

    private Complaint complaint(UUID id, String orderId, String content) {
        Complaint complaint = new Complaint();
        complaint.setId(id);
        complaint.setUserId(UUID.randomUUID());
        complaint.setOrderId(orderId);
        complaint.setContent(content);
        complaint.setStatus(ComplaintStatus.OPEN);
        return complaint;
    }
}
