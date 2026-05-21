package com.enterprise.kb.search.trace.agent;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.enterprise.kb.search.trace.TraceContext;
import com.enterprise.kb.search.trace.TraceContextHolder;
import com.enterprise.kb.search.trace.TraceEvent;
import com.enterprise.kb.search.trace.TraceFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring AI Alibaba 工具调用 Trace 拦截器。
 */
@Component
@RequiredArgsConstructor
public class TraceToolInterceptor extends ToolInterceptor {

    private final TraceFacade traceFacade;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "trace-tool-interceptor";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        TraceContext context = TraceContextHolder.currentOrNoop();
        Instant startedAt = Instant.now();
        Object input = inputOf(request);
        try {
            ToolCallResponse response = handler.call(request);
            traceFacade.recordEvent(context, TraceEvent.toolCallSucceeded(
                    request.getToolName(), request.getToolCallId(), input, outputOf(response), elapsedMs(startedAt)));
            return response;
        } catch (RuntimeException e) {
            traceFacade.recordEvent(context, TraceEvent.toolCallFailed(
                    request.getToolName(), request.getToolCallId(), input, e, elapsedMs(startedAt)));
            throw e;
        }
    }

    private Map<String, Object> inputOf(ToolCallRequest request) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("toolName", request.getToolName());
        input.put("arguments", request.getArguments());
        input.put("context", request.getContext());
        request.getExecutionContext().ifPresent(executionContext -> {
            input.put("threadId", executionContext.threadId().orElse(null));
            input.put("checkpointId", executionContext.checkpointId().orElse(null));
        });
        return input;
    }

    private Map<String, Object> outputOf(ToolCallResponse response) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("status", response.getStatus());
        output.put("result", response.getResult());
        output.put("metadata", response.getMetadata());
        return output;
    }

    private long elapsedMs(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }
}
