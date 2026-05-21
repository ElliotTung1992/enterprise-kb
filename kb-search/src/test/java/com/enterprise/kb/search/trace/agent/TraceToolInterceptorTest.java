package com.enterprise.kb.search.trace.agent;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.enterprise.kb.search.trace.TraceContext;
import com.enterprise.kb.search.trace.TraceContextHolder;
import com.enterprise.kb.search.trace.TraceEvent;
import com.enterprise.kb.search.trace.TraceFacade;
import com.enterprise.kb.search.trace.TraceScope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TraceToolInterceptorTest {

    private final TraceFacade traceFacade = mock(TraceFacade.class);
    private final TraceToolInterceptor interceptor = new TraceToolInterceptor(traceFacade);

    @Test
    void recordsSuccessfulToolCall() throws Exception {
        TraceContext context = context();
        ToolCallRequest request = new ToolCallRequest(
                "searchKnowledgeBase", "{\"query\":\"refund\"}", "call-1", Map.of());

        try (AutoCloseable ignored = TraceContextHolder.bind(scope(context))) {
            ToolCallResponse response = interceptor.interceptToolCall(request,
                    req -> ToolCallResponse.of(req.getToolName(), req.getToolCallId(), "ok"));

            assertThat(response.getResult()).isEqualTo("ok");
        }

        ArgumentCaptor<TraceEvent> eventCaptor = ArgumentCaptor.forClass(TraceEvent.class);
        verify(traceFacade).recordEvent(any(TraceContext.class), eventCaptor.capture());
        assertThat(eventCaptor.getValue().stepType()).isEqualTo("TOOL_CALL");
        assertThat(eventCaptor.getValue().status()).isEqualTo("SUCCEEDED");
        assertThat(eventCaptor.getValue().name()).isEqualTo("searchKnowledgeBase");
        assertThat(eventCaptor.getValue().toolCallId()).isEqualTo("call-1");
    }

    @Test
    void recordsFailedToolCallAndRethrows() throws Exception {
        TraceContext context = context();
        ToolCallRequest request = new ToolCallRequest(
                "searchKnowledgeBase", "{\"query\":\"refund\"}", "call-1", Map.of());

        try (AutoCloseable ignored = TraceContextHolder.bind(scope(context))) {
            assertThatThrownBy(() -> interceptor.interceptToolCall(request, req -> {
                throw new IllegalStateException("boom");
            })).isInstanceOf(IllegalStateException.class);
        }

        ArgumentCaptor<TraceEvent> eventCaptor = ArgumentCaptor.forClass(TraceEvent.class);
        verify(traceFacade).recordEvent(any(TraceContext.class), eventCaptor.capture());
        assertThat(eventCaptor.getValue().status()).isEqualTo("FAILED");
        assertThat(eventCaptor.getValue().error()).isInstanceOf(IllegalStateException.class);
    }

    private TraceContext context() {
        return new TraceContext(UUID.randomUUID(), "AGENTIC_QA", "kb-search-agent",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), true);
    }

    private TraceScope scope(TraceContext context) {
        return new TraceScope() {
            @Override
            public UUID traceId() {
                return context.traceId();
            }

            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public TraceContext context() {
                return context;
            }

            @Override
            public void event(TraceEvent event) {
            }

            @Override
            public void complete(Object output, Integer tokensUsed) {
            }

            @Override
            public void fail(Throwable error) {
            }

            @Override
            public void close() {
            }
        };
    }
}
