package com.enterprise.kb.search.trace.impl;

import com.enterprise.kb.search.dto.TraceCompleteRequest;
import com.enterprise.kb.search.dto.TraceStartRequest;
import com.enterprise.kb.search.dto.TraceStepRequest;
import com.enterprise.kb.search.service.TraceRecorder;
import com.enterprise.kb.search.trace.TraceEvent;
import com.enterprise.kb.search.trace.TraceScope;
import com.enterprise.kb.search.trace.TraceStartCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TraceFacadeImplTest {

    private final TraceRecorder traceRecorder = mock(TraceRecorder.class);
    private final TraceFacadeImpl traceFacade = new TraceFacadeImpl(traceRecorder, new ObjectMapper());

    @Test
    void startReturnsNoopScopeWhenRecorderDoesNotCreateTrace() {
        when(traceRecorder.startTrace(any())).thenReturn(Optional.empty());

        TraceScope scope = traceFacade.start(command());

        assertThat(scope.enabled()).isFalse();
        verify(traceRecorder).startTrace(any(TraceStartRequest.class));
    }

    @Test
    void eventSerializesPayloadAndIncrementsStepIndex() {
        UUID traceId = UUID.randomUUID();
        when(traceRecorder.startTrace(any())).thenReturn(Optional.of(traceId));
        TraceScope scope = traceFacade.start(command());

        scope.event(new TraceEvent("RETRIEVAL", "retrieval", "SUCCEEDED",
                Map.of("query", "refund"), Map.of("count", 2), null,
                null, null, null, 12L, null));
        scope.event(TraceEvent.toolCallSucceeded("searchKnowledgeBase", "call-1",
                Map.of("query", "refund"), Map.of("result", "ok"), 8L));

        ArgumentCaptor<TraceStepRequest> captor = ArgumentCaptor.forClass(TraceStepRequest.class);
        verify(traceRecorder, times(2)).recordStep(any(UUID.class), captor.capture());

        assertThat(captor.getAllValues().get(0).stepIndex()).isEqualTo(1);
        assertThat(captor.getAllValues().get(0).inputJson()).contains("\"query\":\"refund\"");
        assertThat(captor.getAllValues().get(1).stepIndex()).isEqualTo(2);
        assertThat(captor.getAllValues().get(1).toolName()).isEqualTo("searchKnowledgeBase");
        assertThat(captor.getAllValues().get(1).toolCallId()).isEqualTo("call-1");
    }

    @Test
    void completeIsIdempotent() {
        UUID traceId = UUID.randomUUID();
        when(traceRecorder.startTrace(any())).thenReturn(Optional.of(traceId));
        TraceScope scope = traceFacade.start(command());

        scope.complete(Map.of("answer", "ok"), 7);
        scope.close();

        ArgumentCaptor<TraceCompleteRequest> captor = ArgumentCaptor.forClass(TraceCompleteRequest.class);
        verify(traceRecorder).completeTrace(any(UUID.class), captor.capture());
        assertThat(captor.getValue().rawOutputJson()).contains("\"answer\":\"ok\"");
        assertThat(captor.getValue().tokensUsed()).isEqualTo(7);
    }

    @Test
    void recordEventIgnoresNoopContext() {
        TraceRecorder localRecorder = mock(TraceRecorder.class);
        TraceFacadeImpl localFacade = new TraceFacadeImpl(localRecorder, new ObjectMapper());

        localFacade.recordEvent(null, TraceEvent.modelCallSucceeded("agent", "in", "out", 1L));

        verifyNoInteractions(localRecorder);
    }

    private TraceStartCommand command() {
        return new TraceStartCommand(
                "AGENTIC_QA",
                "kb-search-agent",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "DASHSCOPE",
                "question",
                Map.of("question", "question"));
    }

}
