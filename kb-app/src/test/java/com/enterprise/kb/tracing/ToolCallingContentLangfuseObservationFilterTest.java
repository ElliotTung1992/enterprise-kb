package com.enterprise.kb.tracing;

import com.enterprise.kb.common.tracing.TracingAttributes;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.ai.tool.observation.ToolCallingObservationDocumentation;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallingContentLangfuseObservationFilterTest {

    private ToolCallingObservationContext context(String arguments, String result) {
        ToolCallingObservationContext context = ToolCallingObservationContext.builder()
                .toolDefinition(ToolDefinition.builder()
                        .name("searchKnowledgeBase").description("检索").inputSchema("{}").build())
                .toolMetadata(ToolMetadata.builder().build())
                .toolCallArguments(arguments)
                .build();
        if (result != null) {
            context.setToolCallResult(result);
        }
        return context;
    }

    @Test
    void mapsToolArgumentsAndResultToLangfuseObservationAttributes() {
        ToolCallingObservationContext context = context("{\"query\":\"退款流程\"}", "=== 检索结果 ===");

        new ToolCallingContentLangfuseObservationFilter(4000).map(context);

        assertThat(highCardinalityValue(context, toolCallArgumentsKey()))
                .isEqualTo("{\"query\":\"退款流程\"}");
        assertThat(highCardinalityValue(context, toolCallResultKey()))
                .isEqualTo("=== 检索结果 ===");
        assertThat(highCardinalityValue(context, TracingAttributes.OBSERVATION_INPUT))
                .isEqualTo("{\"query\":\"退款流程\"}");
        assertThat(highCardinalityValue(context, TracingAttributes.OBSERVATION_OUTPUT))
                .isEqualTo("=== 检索结果 ===");
    }

    @Test
    void ignoresNonToolObservationContext() {
        Observation.Context context = new Observation.Context();

        new ToolCallingContentLangfuseObservationFilter(4000).map(context);

        assertThat(context.getHighCardinalityKeyValues()).isEmpty();
    }

    @Test
    void skipsBlankResultBeforeToolReturns() {
        ToolCallingObservationContext context = context("{\"query\":\"x\"}", null);

        new ToolCallingContentLangfuseObservationFilter(4000).map(context);

        assertThat(highCardinalityValue(context, toolCallArgumentsKey())).isEqualTo("{\"query\":\"x\"}");
        assertThat(highCardinalityValue(context, toolCallResultKey())).isNull();
        assertThat(highCardinalityValue(context, TracingAttributes.OBSERVATION_INPUT)).isEqualTo("{\"query\":\"x\"}");
        assertThat(highCardinalityValue(context, TracingAttributes.OBSERVATION_OUTPUT)).isNull();
    }

    @Test
    void redactsAndTruncatesToolContent() {
        ToolCallingObservationContext context = context(
                "api_key: sk-1234567890ABCDEFGHIJK 还有很长很长很长很长很长很长的参数内容",
                "联系邮箱 user@example.com，电话 13800138000");

        new ToolCallingContentLangfuseObservationFilter(20).map(context);

        assertThat(highCardinalityValue(context, toolCallArgumentsKey()))
                .contains("[REDACTED]")
                .contains("truncated")
                .doesNotContain("sk-1234567890ABCDEFGHIJK");
        assertThat(highCardinalityValue(context, toolCallResultKey()))
                .contains("[REDACTED]")
                .doesNotContain("user@example.com")
                .doesNotContain("13800138000");
        assertThat(highCardinalityValue(context, TracingAttributes.OBSERVATION_INPUT))
                .contains("[REDACTED]")
                .contains("truncated");
        assertThat(highCardinalityValue(context, TracingAttributes.OBSERVATION_OUTPUT))
                .contains("[REDACTED]");
    }

    private String toolCallArgumentsKey() {
        return ToolCallingObservationDocumentation.HighCardinalityKeyNames.TOOL_CALL_ARGUMENTS.asString();
    }

    private String toolCallResultKey() {
        return ToolCallingObservationDocumentation.HighCardinalityKeyNames.TOOL_CALL_RESULT.asString();
    }

    private String highCardinalityValue(Observation.Context context, String key) {
        return context.getHighCardinalityKeyValues().stream()
                .filter(kv -> key.equals(kv.getKey()))
                .map(KeyValue::getValue)
                .findFirst()
                .orElse(null);
    }
}
