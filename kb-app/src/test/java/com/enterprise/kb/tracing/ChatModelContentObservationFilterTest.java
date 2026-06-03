package com.enterprise.kb.tracing;

import com.enterprise.kb.common.tracing.TracingAttributes;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
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
                .contains("SYSTEM:\n你是知识库助手，按引用回答")
                .contains("USER:\n退款流程是什么？");
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
    void mapsToolCallOnlyAssistantMessageToObservationOutput() {
        ChatModelObservationContext context = ChatModelObservationContext.builder()
                .provider("openai")
                .prompt(new Prompt(List.of(new UserMessage("告诉我约拿单和大卫的故事"))))
                .build();
        AssistantMessage toolCallMessage = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1", "function", "searchKnowledgeBase", "{\"query\":\"约拿单和大卫\"}")))
                .build();
        context.setResponse(new ChatResponse(List.of(new Generation(toolCallMessage))));

        new ChatModelContentObservationFilter(200, 200).map(context);

        assertThat(highCardinalityValue(context, TracingAttributes.OBSERVATION_OUTPUT))
                .isEqualTo("""
                        tool_calls:
                        - id: call-1
                          type: function
                          name: searchKnowledgeBase
                          arguments:
                            {\"query\":\"约拿单和大卫\"}""");
    }

    @Test
    void mapsAssistantTextAndToolCallsTogether() {
        ChatModelObservationContext context = ChatModelObservationContext.builder()
                .provider("openai")
                .prompt(new Prompt(List.of(new UserMessage("告诉我摩西的故事"))))
                .build();
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("我先检索知识库。")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1", "function", "searchKnowledgeBase", "{\"query\":\"摩西的故事\"}")))
                .build();
        context.setResponse(new ChatResponse(List.of(new Generation(assistantMessage))));

        new ChatModelContentObservationFilter(200, 200).map(context);

        assertThat(highCardinalityValue(context, TracingAttributes.OBSERVATION_OUTPUT))
                .isEqualTo("""
                        text:
                          我先检索知识库。
                        tool_calls:
                        - id: call-1
                          type: function
                          name: searchKnowledgeBase
                          arguments:
                            {\"query\":\"摩西的故事\"}""");
    }

    @Test
    void mapsToolCallAndToolResponseMessagesInPromptInput() {
        AssistantMessage toolCallMessage = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1", "function", "searchKnowledgeBase", "{\"query\":\"摩西的故事\"}")))
                .build();
        ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "call-1", "searchKnowledgeBase", "=== Markdown child 检索结果 ===")))
                .build();
        ChatModelObservationContext context = ChatModelObservationContext.builder()
                .provider("openai")
                .prompt(new Prompt(List.of(
                        new UserMessage("告诉我摩西的故事"),
                        toolCallMessage,
                        toolResponseMessage)))
                .build();
        context.setResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("摩西的故事...")))));

        new ChatModelContentObservationFilter(500, 200).map(context);

        assertThat(highCardinalityValue(context, TracingAttributes.OBSERVATION_INPUT))
                .contains("""
                        ASSISTANT:
                        tool_calls:
                        - id: call-1
                          type: function
                          name: searchKnowledgeBase
                          arguments:
                            {\"query\":\"摩西的故事\"}""")
                .contains("""
                        TOOL:
                        tool_responses:
                        - id: call-1
                          name: searchKnowledgeBase
                          response:
                            === Markdown child 检索结果 ===""");
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
