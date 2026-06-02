package com.enterprise.kb.tracing;

import com.enterprise.kb.common.tracing.SensitiveDataRedactor;
import com.enterprise.kb.common.tracing.TracingAttributes;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 将 Spring AI ChatModel observation 的模型输入/输出映射到 LangFuse 原生属性。
 *
 * <p>Spring AI 的 prompt/completion 内容默认不会自动变成
 * {@code langfuse.observation.input/output}。本过滤器在 observation stop 前从
 * {@link ChatModelObservationContext} 读取实际请求与响应，写入 LangFuse 可识别的高基数
 * KeyValue，并复用项目统一脱敏截断规则。</p>
 */
public class ChatModelContentObservationFilter implements ObservationFilter {

    private final int maxPromptChars;
    private final int maxCompletionChars;

    /**
     * @param maxPromptChars prompt 正文截断上限
     * @param maxCompletionChars completion 正文截断上限
     */
    public ChatModelContentObservationFilter(int maxPromptChars, int maxCompletionChars) {
        this.maxPromptChars = maxPromptChars;
        this.maxCompletionChars = maxCompletionChars;
    }

    @Override
    public Observation.Context map(Observation.Context context) {
        if (!(context instanceof ChatModelObservationContext chatContext)) {
            return context;
        }

        String input = promptText(chatContext.getRequest());
        if (hasText(input)) {
            context.addHighCardinalityKeyValue(KeyValue.of(
                    TracingAttributes.OBSERVATION_INPUT,
                    SensitiveDataRedactor.redactAndTruncate(input, maxPromptChars)));
        }

        String output = completionText(chatContext.getResponse());
        if (hasText(output)) {
            context.addHighCardinalityKeyValue(KeyValue.of(
                    TracingAttributes.OBSERVATION_OUTPUT,
                    SensitiveDataRedactor.redactAndTruncate(output, maxCompletionChars)));
        }

        return context;
    }

    private String promptText(Prompt prompt) {
        if (prompt == null || prompt.getInstructions() == null) {
            return "";
        }
        return prompt.getInstructions().stream()
                .map(message -> message.getMessageType() + ": " + nullToEmpty(message.getText()))
                .collect(Collectors.joining("\n\n"));
    }

    private String completionText(ChatResponse response) {
        if (response == null || response.getResults() == null) {
            return "";
        }
        return response.getResults().stream()
                .filter(Objects::nonNull)
                .map(Generation::getOutput)
                .filter(Objects::nonNull)
                .map(message -> nullToEmpty(message.getText()))
                .filter(this::hasText)
                .collect(Collectors.joining("\n\n"));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
