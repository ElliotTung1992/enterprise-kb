package com.enterprise.kb.search.service.impl;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.enterprise.kb.common.constants.CompensationType;
import com.enterprise.kb.common.constants.ComplaintPlanStatus;
import com.enterprise.kb.common.constants.ResponsibleParty;
import com.enterprise.kb.search.dto.ComplaintPlanModifyRequest;
import com.enterprise.kb.search.model.ComplaintPlan;
import com.enterprise.kb.search.service.ComplaintEscalationService;
import com.enterprise.kb.search.service.ComplaintExecutorService;
import com.enterprise.kb.search.service.ComplaintReplannerService;
import com.enterprise.kb.search.service.ComplaintResponsibilityInferenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintWorkflowServiceImplTest {

    @Mock ComplaintEscalationService complaintEscalationService;
    @Mock ComplaintExecutorService complaintExecutorService;
    @Mock ComplaintReplannerService complaintReplannerService;
    @Mock ComplaintResponsibilityInferenceService responsibilityInferenceService;
    @Mock CompiledGraph compiledGraph;

    ComplaintWorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ComplaintWorkflowServiceImpl(
                complaintEscalationService,
                complaintExecutorService,
                complaintReplannerService,
                responsibilityInferenceService,
                null,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "compiledGraph", compiledGraph);
    }

    @Test
    void resumeApprovedContinuesFromUpdatedCheckpointForPlanIdThread() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        RunnableConfig updatedConfig = RunnableConfig.builder()
                .threadId(planId.toString())
                .checkPointId("checkpoint-after-review")
                .nextNode("applyDecision")
                .build();

        StateSnapshot snapshot = mock(StateSnapshot.class);
        when(snapshot.config()).thenReturn(updatedConfig);
        when(snapshot.state()).thenReturn(new OverAllState(Map.of("planId", planId.toString())));
        when(compiledGraph.getState(any(RunnableConfig.class))).thenReturn(snapshot);
        when(compiledGraph.updateState(any(RunnableConfig.class), any())).thenReturn(updatedConfig);
        when(compiledGraph.cloneState(any())).thenReturn(new OverAllState(Map.of("planId", planId.toString())));
        when(compiledGraph.streamFromInitialNode(any(OverAllState.class), any(RunnableConfig.class))).thenReturn(Flux.empty());

        ComplaintPlan plan = new ComplaintPlan();
        plan.setId(planId);
        plan.setStatus(ComplaintPlanStatus.PENDING_REVIEW);
        when(complaintEscalationService.findPlanById(planId)).thenReturn(plan);

        service.resumeApproved(planId, reviewerId, "同意执行");

        ArgumentCaptor<RunnableConfig> streamConfig = ArgumentCaptor.forClass(RunnableConfig.class);
        verify(compiledGraph).streamFromInitialNode(any(OverAllState.class), streamConfig.capture());

        RunnableConfig config = streamConfig.getValue();
        assertThat(config.threadId()).contains(planId.toString());
        assertThat(config.checkPointId()).contains("checkpoint-after-review");
        assertThat(config.metadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY)).isPresent();

        assertThat(captureStateUpdates()).containsEntry("planId", planId.toString());
    }

    @Test
    void resumeRejectedIncludesStringPlanIdInStateUpdate() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        givenResumeCheckpoint(planId);

        ComplaintPlan plan = new ComplaintPlan();
        plan.setId(planId);
        plan.setStatus(ComplaintPlanStatus.PENDING_REVIEW);
        when(complaintEscalationService.findPlanById(planId)).thenReturn(plan);

        service.resumeRejected(planId, reviewerId, "不同意");

        assertThat(captureStateUpdates()).containsEntry("planId", planId.toString());
    }

    @Test
    void resumeModifiedIncludesStringPlanIdInStateUpdate() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        givenResumeCheckpoint(planId);

        ComplaintPlan plan = new ComplaintPlan();
        plan.setId(planId);
        plan.setStatus(ComplaintPlanStatus.PENDING_REVIEW);
        when(complaintEscalationService.findPlanById(planId)).thenReturn(plan);

        ComplaintPlanModifyRequest req = new ComplaintPlanModifyRequest(
                ResponsibleParty.PLATFORM,
                CompensationType.REFUND,
                null,
                "改为平台退款"
        );
        service.resumeModified(planId, reviewerId, req);

        assertThat(captureStateUpdates()).containsEntry("planId", planId.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureStateUpdates() throws Exception {
        ArgumentCaptor<Map<String, Object>> updates = ArgumentCaptor.forClass(Map.class);
        verify(compiledGraph).updateState(any(RunnableConfig.class), updates.capture());
        return updates.getValue();
    }

    private void givenResumeCheckpoint(UUID planId) throws Exception {
        RunnableConfig updatedConfig = RunnableConfig.builder()
                .threadId(planId.toString())
                .checkPointId("checkpoint-after-review")
                .nextNode("applyDecision")
                .build();

        StateSnapshot snapshot = mock(StateSnapshot.class);
        when(snapshot.config()).thenReturn(updatedConfig);
        when(snapshot.state()).thenReturn(new OverAllState(Map.of("planId", planId.toString())));
        when(compiledGraph.getState(any(RunnableConfig.class))).thenReturn(snapshot);
        when(compiledGraph.updateState(any(RunnableConfig.class), any())).thenReturn(updatedConfig);
        when(compiledGraph.cloneState(any())).thenReturn(new OverAllState(Map.of("planId", planId.toString())));
        when(compiledGraph.streamFromInitialNode(any(OverAllState.class), any(RunnableConfig.class))).thenReturn(Flux.empty());
    }
}
