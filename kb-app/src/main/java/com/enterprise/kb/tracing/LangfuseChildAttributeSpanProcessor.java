package com.enterprise.kb.tracing;

import com.enterprise.kb.common.tracing.TracingAttributes;
import com.enterprise.kb.common.tracing.TracingContextHolder;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

import java.util.Map;

/**
 * 把业务根 span 的 LangFuse 业务属性复制到同一 trace 的每个子 span（ADR-015 D4 / step 7）。
 *
 * <p>service 入口的业务根 span 会把 {@code langfuse.user.id} / {@code langfuse.session.id} /
 * metadata 写入 {@link TracingContextHolder}（thread-local，且经 context-propagation 跨线程传播）。
 * 本 {@link SpanProcessor} 在每个 span {@code onStart} 时读取 holder，把这些属性补到 span 上，
 * 使 LangFuse 的 observation 级过滤/聚合在 LLM / tool / retrieval 子 span 上同样可用——
 * 否则这些属性只在根 span 可见。</p>
 *
 * <p>数据来源是本地 thread-local 而非 OTel baggage，因此业务属性<b>不会</b>随下游 HTTP 请求
 * 透传给模型提供商。</p>
 */
public class LangfuseChildAttributeSpanProcessor implements SpanProcessor {

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        Map<String, String> attributes = TracingContextHolder.current();
        if (attributes.isEmpty()) {
            return;
        }
        attributes.forEach((key, value) -> {
            if (TracingAttributes.OBSERVATION_PROMPT_VERSION.equals(key)) {
                try {
                    span.setAttribute(key, Long.parseLong(value));
                    return;
                } catch (NumberFormatException ignored) {
                    // 版本异常时降级按字符串写入，避免影响 span 创建。
                }
            }
            span.setAttribute(key, value);
        });
    }

    @Override
    public boolean isStartRequired() {
        return true;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        // 无需在 span 结束时处理
    }

    @Override
    public boolean isEndRequired() {
        return false;
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode forceFlush() {
        return CompletableResultCode.ofSuccess();
    }
}
