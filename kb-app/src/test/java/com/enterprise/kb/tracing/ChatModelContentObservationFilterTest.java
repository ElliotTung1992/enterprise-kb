package com.enterprise.kb.tracing;

import com.enterprise.kb.common.tracing.TracingAttributes;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatModelContentObservationFilterTest {

    @Test
    void mapsChatModelPromptAndCompletionToLangfuseObservationAttributes() {
        ChatModelObservationContext context = ChatModelObservationContext.builder()
                .provider("dashscope")
                .prompt(new Prompt(List.of(
                        new SystemMessage("你是知识库助手，按引用回答"),
                        new UserMessage("退款流程是什么？"))))
                .build();
        context.setResponse(new ChatResponse(List.of(
                new Generation(new AssistantMessage("请按工单流程提交退款申请。")))));

        new ChatModelContentObservationFilter(200, 200).map(context);

        assertThat(highCardinalityValue(context, TracingAttributes.OBSERVATION_INPUT))
                .contains("SYSTEM: 你是知识库助手，按引用回答")
                .contains("USER: 退款流程是什么？");
        assertThat(highCardinalityValue(context, TracingAttributes.OBSERVATION_OUTPUT))
                .isEqualTo("请按工单流程提交退款申请。");
    }

    @Test
    void ignoresNonChatModelObservationContext() {
        Observation.Context context = new Observation.Context();

        new ChatModelContentObservationFilter(200, 200).map(context);

        assertThat(context.getHighCardinalityKeyValues()).isEmpty();
    }

    @Test
    void redactsAndTruncatesMappedContent() {
        ChatModelObservationContext context = ChatModelObservationContext.builder()
                .provider("dashscope")
                .prompt(new Prompt(List.of(new UserMessage("api_key: sk-1234567890ABCDEFGHIJK 明天退款"))))
                .build();
        context.setResponse(new ChatResponse(List.of(
                new Generation(new AssistantMessage("token: Bearer abcdefghijklmnopqrstuvwxyz")))));

        new ChatModelContentObservationFilter(30, 20).map(context);

        assertThat(highCardinalityValue(context, TracingAttributes.OBSERVATION_INPUT))
                .contains("[REDACTED]")
                .hasSizeLessThanOrEqualTo(45);
        assertThat(highCardinalityValue(context, TracingAttributes.OBSERVATION_OUTPUT))
                .contains("[REDACTED]")
                .hasSizeLessThanOrEqualTo(35);
    }

    private String highCardinalityValue(Observation.Context context, String key) {
        return context.getHighCardinalityKeyValues().stream()
                .filter(kv -> key.equals(kv.getKey()))
                .map(KeyValue::getValue)
                .findFirst()
                .orElse(null);
    }
}
