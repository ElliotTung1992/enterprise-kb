package com.enterprise.kb.search.trace.agent;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.enterprise.kb.search.trace.TraceContext;
import com.enterprise.kb.search.trace.TraceContextHolder;
import com.enterprise.kb.search.trace.TraceEvent;
import com.enterprise.kb.search.trace.TraceFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring AI Alibaba 模型调用 Trace 拦截器。
 */
@Component
@RequiredArgsConstructor
public class TraceModelInterceptor extends ModelInterceptor {

    private final TraceFacade traceFacade;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "trace-model-interceptor";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        TraceContext context = TraceContextHolder.currentOrNoop();
        Instant startedAt = Instant.now();
        Object input = inputOf(request);
        try {
            ModelResponse response = handler.call(request);
            traceFacade.recordEvent(context, TraceEvent.modelCallSucceeded(
                    context.agentName(), input, outputOf(response), elapsedMs(startedAt)));
            return response;
        } catch (RuntimeException e) {
            traceFacade.recordEvent(context, TraceEvent.modelCallFailed(
                    context.agentName(), input, e, elapsedMs(startedAt)));
            throw e;
        }
    }

    private Map<String, Object> inputOf(ModelRequest request) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("systemMessage", request.getSystemMessage() == null ? null : request.getSystemMessage().getText());
        input.put("messages", messagesOf(request.getMessages()));
        input.put("tools", request.getTools());
        input.put("toolDescriptions", request.getToolDescriptions());
        input.put("context", request.getContext());
        return input;
    }

    private Map<String, Object> outputOf(ModelResponse response) {
        Map<String, Object> output = new LinkedHashMap<>();
        Object message = response.getMessage();
        output.put("message", message instanceof AssistantMessage assistant ? messageOf(assistant) : message);
        ChatResponse chatResponse = response.getChatResponse();
        output.put("hasToolCalls", chatResponse != null && chatResponse.hasToolCalls());
        return output;
    }

    private List<Map<String, Object>> messagesOf(List<Message> messages) {
        if (messages == null) {
            return List.of();
        }
        return messages.stream().map(this::messageOf).toList();
    }

    private Map<String, Object> messageOf(Message message) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", message.getMessageType());
        value.put("text", message.getText());
        if (message instanceof AssistantMessage assistantMessage) {
            value.put("hasToolCalls", assistantMessage.hasToolCalls());
            value.put("toolCalls", assistantMessage.getToolCalls());
        }
        return value;
    }

    private long elapsedMs(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }
}
