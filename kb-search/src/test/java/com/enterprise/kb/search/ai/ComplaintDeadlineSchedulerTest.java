package com.enterprise.kb.search.ai;

import com.enterprise.kb.search.mapper.ComplaintPlanMapper;
import com.enterprise.kb.search.model.ComplaintPlan;
import com.enterprise.kb.search.service.ComplaintWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintDeadlineSchedulerTest {

    @Mock ComplaintPlanMapper complaintPlanMapper;
    @Mock ComplaintWorkflowService complaintWorkflowService;
    @InjectMocks ComplaintDeadlineScheduler scheduler;

    // ---- Slice 1: 无超时计划 → 不触发重规划 ----

    @Test
    void checkDeadlines_doesNothingWhenNoOverduePlans() {
        when(complaintPlanMapper.findExecutingPastDeadline(any())).thenReturn(List.of());

        scheduler.checkDeadlines();

        verify(complaintWorkflowService, never()).triggerReplan(any());
    }

    // ---- Slice 2: 有超时计划 → 每个都触发重规划 ----

    @Test
    void checkDeadlines_callsTriggerReplanForEachOverduePlan() {
        UUID planId1 = UUID.randomUUID();
        UUID planId2 = UUID.randomUUID();
        when(complaintPlanMapper.findExecutingPastDeadline(any()))
                .thenReturn(List.of(overduePlan(planId1), overduePlan(planId2)));

        scheduler.checkDeadlines();

        verify(complaintWorkflowService).triggerReplan(planId1);
        verify(complaintWorkflowService).triggerReplan(planId2);
    }

    // ---- Slice 3: 某计划失败 → 继续处理剩余计划 ----

    @Test
    void checkDeadlines_continuesProcessingWhenOnePlanFails() {
        UUID planId1 = UUID.randomUUID();
        UUID planId2 = UUID.randomUUID();
        when(complaintPlanMapper.findExecutingPastDeadline(any()))
                .thenReturn(List.of(overduePlan(planId1), overduePlan(planId2)));
        doThrow(new RuntimeException("重规划失败")).when(complaintWorkflowService).triggerReplan(planId1);

        scheduler.checkDeadlines();

        verify(complaintWorkflowService).triggerReplan(planId1);
        verify(complaintWorkflowService).triggerReplan(planId2);
    }

    private ComplaintPlan overduePlan(UUID planId) {
        ComplaintPlan plan = new ComplaintPlan();
        plan.setId(planId);
        plan.setComplaintId(UUID.randomUUID());
        plan.setNextCheckAt(Instant.now().minusSeconds(120));
        return plan;
    }
}
