package com.enterprise.kb.tracing;

import com.enterprise.kb.common.tracing.SensitiveDataRedactor;
import com.enterprise.kb.common.tracing.TracingAttributes;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.ai.tool.observation.ToolCallingObservationDocumentation;

/**
 * 将 Spring AI tool observation 的入参 / 返回写入安全版正文属性。
 *
 * <p>Spring AI 原生 {@code ToolCallingContentObservationFilter} 会把 tool 入参 / 返回原样写成
 * {@code spring.ai.tool.call.arguments} / {@code spring.ai.tool.call.result} 高基数 key。为避免敏感内容绕过
 * 项目脱敏策略，本过滤器接管该职责：从 {@link ToolCallingObservationContext} 读取 arguments / result，
 * 先按 {@code max-tool-chars} 脱敏截断，再同时写入 Spring AI 原生 key 与 LangFuse
 * {@code langfuse.observation.input/output}。</p>
 *
 * <p>覆盖 {@link com.enterprise.kb.search.ai.TracingToolInterceptor}（ReactAgent 路径）与未来任何走
 * Spring AI 原生 tool observation 的调用，二者共用同一套 LangFuse 语义。</p>
 */
public class ToolCallingContentLangfuseObservationFilter implements ObservationFilter {

    private final int maxToolChars;

    /**
     * @param maxToolChars tool 入参 / 返回正文截断上限
     */
    public ToolCallingContentLangfuseObservationFilter(int maxToolChars) {
        this.maxToolChars = maxToolChars;
    }

    @Override
    public Observation.Context map(Observation.Context context) {
        if (!(context instanceof ToolCallingObservationContext toolContext)) {
            return context;
        }

        String input = toolContext.getToolCallArguments();
        if (hasText(input)) {
            String sanitized = SensitiveDataRedactor.redactAndTruncate(input, maxToolChars);
            addHighCardinality(context,
                    ToolCallingObservationDocumentation.HighCardinalityKeyNames.TOOL_CALL_ARGUMENTS.asString(),
                    sanitized);
            addHighCardinality(context, TracingAttributes.OBSERVATION_INPUT, sanitized);
        }

        String output = toolContext.getToolCallResult();
        if (hasText(output)) {
            String sanitized = SensitiveDataRedactor.redactAndTruncate(output, maxToolChars);
            addHighCardinality(context,
                    ToolCallingObservationDocumentation.HighCardinalityKeyNames.TOOL_CALL_RESULT.asString(),
                    sanitized);
            addHighCardinality(context, TracingAttributes.OBSERVATION_OUTPUT, sanitized);
        }

        return context;
    }

    private void addHighCardinality(Observation.Context context, String key, String value) {
        context.addHighCardinalityKeyValue(KeyValue.of(key, value));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
