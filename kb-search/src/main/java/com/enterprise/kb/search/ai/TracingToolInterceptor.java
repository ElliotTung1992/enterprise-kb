package com.enterprise.kb.search.ai;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.enterprise.kb.common.tracing.TracingSupport;
import io.micrometer.observation.ObservationRegistry;

/**
 * 为 ReactAgent 的每次工具调用产出 tool span（ADR-015 C2）。
 *
 * <p>框架原生的 around 式拦截器：包住 {@link ToolCallHandler#call(ToolCallRequest)}（真正执行工具），
 * per-tool-call 粒度产出 {@code gen_ai.tool.execution} span，业务工具体（{@code searchKnowledgeBase} /
 * {@code readFullSection}）零侵入。挂载方式：{@code ReactAgent.builder().interceptors(...)}。</p>
 *
 * <p>入参（tool arguments）写 {@code langfuse.observation.input}、返回（tool result）写
 * {@code langfuse.observation.output}，均由 {@link TracingSupport} 内置脱敏 + 截断
 * （design-langfuse-io-mapping D2）；工具执行抛异常时由 Observation 自动记录。</p>
 */
public class TracingToolInterceptor extends ToolInterceptor {

    private final ObservationRegistry observationRegistry;
    private final int maxToolChars;

    public TracingToolInterceptor(ObservationRegistry observationRegistry, int maxToolChars) {
        this.observationRegistry = observationRegistry;
        this.maxToolChars = maxToolChars;
    }

    @Override
    public String getName() {
        return "kb-tracing-tool";
    }

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        return TracingSupport.span(observationRegistry, true, "gen_ai.tool.execution")
                .tag("gen_ai.tool.name", request.getToolName())
                .tag("gen_ai.tool.call.id", request.getToolCallId())
                .input(request.getArguments(), maxToolChars)
                .outputFrom((ToolCallResponse resp) -> resp.getResult(), maxToolChars)
                .observe(() -> handler.call(request));
    }
}
