package com.enterprise.kb.search.trace.advisor;

import com.enterprise.kb.search.trace.TraceContext;
import com.enterprise.kb.search.trace.TraceContextHolder;
import com.enterprise.kb.search.trace.TraceEvent;
import com.enterprise.kb.search.trace.TraceFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
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
 * 普通 ChatClient 模型调用 Trace Advisor。
 */
@Component
@RequiredArgsConstructor
public class TraceChatClientAdvisor implements CallAdvisor {

    private final TraceFacade traceFacade;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "trace-chat-client-advisor";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOrder() {
        return Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER + 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        TraceContext context = TraceContextHolder.currentOrNoop();
        Instant startedAt = Instant.now();
        Object input = inputOf(request);
        try {
            ChatClientResponse response = chain.nextCall(request);
            traceFacade.recordEvent(context, TraceEvent.modelCallSucceeded(
                    context.agentName(), input, outputOf(response), elapsedMs(startedAt)));
            return response;
        } catch (RuntimeException e) {
            traceFacade.recordEvent(context, TraceEvent.modelCallFailed(
                    context.agentName(), input, e, elapsedMs(startedAt)));
            throw e;
        }
    }

    private Map<String, Object> inputOf(ChatClientRequest request) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("messages", request.prompt() == null ? List.of() : messagesOf(request.prompt().getInstructions()));
        input.put("context", request.context());
        return input;
    }

    private Map<String, Object> outputOf(ChatClientResponse response) {
        Map<String, Object> output = new LinkedHashMap<>();
        ChatResponse chatResponse = response.chatResponse();
        output.put("hasToolCalls", chatResponse != null && chatResponse.hasToolCalls());
        output.put("message", chatResponse == null || chatResponse.getResult() == null
                ? null
                : messageOf(chatResponse.getResult().getOutput()));
        output.put("context", response.context());
        return output;
    }

    private List<Map<String, Object>> messagesOf(List<Message> messages) {
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
