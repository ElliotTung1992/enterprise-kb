package com.enterprise.kb.search.ai;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.observation.DefaultToolCallingObservationConvention;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.ai.tool.observation.ToolCallingObservationDocumentation;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 为 Alibaba {@code ReactAgent} 的每次工具调用产出 tool span（ADR-015 tool span 规约）。
 *
 * <p>Alibaba {@code ReactAgent} 1.1.2.2 的工具执行路径不走 Spring AI {@code DefaultToolCallingManager}，
 * 因此 {@link ToolInterceptor} 是当前版本唯一稳定、低侵入的工具执行观测入口。但 interceptor 只负责「接入
 * 位置」，不发明新规约：tool span 的命名 / 低基数字段 / 高基数字段一律复用 Spring AI 原生
 * {@code spring.ai.tool} 规约（{@link DefaultToolCallingObservationConvention}），使 ReactAgent 产生的
 * tool span 与 Spring AI 其他 tool observation 在语义上保持一致。</p>
 *
 * <ul>
 *   <li>span 名 {@code spring.ai.tool}；低基数 {@code spring.ai.kind} / {@code spring.ai.tool.definition.name}
 *       / {@code gen_ai.operation.name} / {@code gen_ai.system}。</li>
 *   <li>tool 入参 / 返回的高基数正文（{@code spring.ai.tool.call.arguments/result}）由 Spring AI 原生
 *       {@code ToolCallingContentObservationFilter} 写入，并由项目的 langfuse 映射 filter 镜像到
 *       {@code langfuse.observation.input/output}；脱敏截断在全局 filter 兜底。</li>
 *   <li>真实 {@link ToolDefinition} / {@link ToolMetadata} 由构建 Agent 时注册的 {@link ToolCallback}
 *       列表按 tool name 解析；缺失时用最小 fallback definition。</li>
 * </ul>
 *
 * <p>挂载方式：{@code ReactAgent.builder().interceptors(...)}。业务工具体（{@code searchKnowledgeBase} /
 * {@code readFullSection}）零侵入。</p>
 */
public class TracingToolInterceptor extends ToolInterceptor {

    private static final DefaultToolCallingObservationConvention CONVENTION =
            new DefaultToolCallingObservationConvention();

    private final ObservationRegistry observationRegistry;
    private final Map<String, ToolCallback> toolCallbacksByName;

    /**
     * @param observationRegistry 可观测注册表
     * @param toolCallbacks       注册给 Agent 的工具回调，用于按 tool name 解析真实 ToolDefinition / metadata
     */
    public TracingToolInterceptor(ObservationRegistry observationRegistry, List<ToolCallback> toolCallbacks) {
        this.observationRegistry = observationRegistry;
        this.toolCallbacksByName = toolCallbacks.stream()
                .collect(Collectors.toMap(cb -> cb.getToolDefinition().name(), Function.identity(), (a, b) -> a));
    }

    @Override
    public String getName() {
        return "kb-tracing-tool";
    }

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        ToolCallback callback = toolCallbacksByName.get(request.getToolName());
        ToolDefinition toolDefinition = callback != null
                ? callback.getToolDefinition()
                : fallbackDefinition(request.getToolName());
        ToolMetadata toolMetadata = callback != null
                ? callback.getToolMetadata()
                : ToolMetadata.builder().build();

        ToolCallingObservationContext context = ToolCallingObservationContext.builder()
                .toolDefinition(toolDefinition)
                .toolMetadata(toolMetadata)
                .toolCallArguments(request.getArguments())
                .build();

        return ToolCallingObservationDocumentation.TOOL_CALL
                .observation(null, CONVENTION, () -> context, observationRegistry)
                .observe(() -> {
                    ToolCallResponse response = handler.call(request);
                    context.setToolCallResult(response.getResult());
                    return response;
                });
    }

    private ToolDefinition fallbackDefinition(String toolName) {
        return ToolDefinition.builder()
                .name(toolName != null ? toolName : "unknown")
                .description("")
                .inputSchema("{}")
                .build();
    }
}
