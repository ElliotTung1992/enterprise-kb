package com.enterprise.kb.tracing;

import com.enterprise.kb.common.tracing.SensitiveDataRedactor;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;

/**
 * 进 trace 高基数 tag 的脱敏 + 截断兜底过滤器（ADR-015 D5）。
 *
 * <p>对所有 observation 的<b>高基数</b> KeyValue（正文类内容多挂在此）做正则脱敏 + 长度截断，
 * 作为统一安全网。埋点源头（tool 拦截器 / 检索 span / 业务根 span 的 input/output）已在
 * {@code TracingSupport} 内按各自上限脱敏截断；本过滤器再兜一道，并覆盖框架自身写入的高基数 tag。</p>
 *
 * <p><b>覆盖边界：</b>本过滤器只作用于 observation 的 KeyValue 路径，是 KeyValue 的兜底，
 * <b>不</b>覆盖所有直接写到 span 上的 attribute。业务 span 经
 * {@code TracingSupport.traceInput/input/traceOutputFrom/outputFrom} 写入的
 * {@code langfuse.*.input/output} 在写入源头已强制脱敏截断，无需也不依赖本过滤器。</p>
 *
 * <p>Spring AI ChatModel 的 prompt/completion 由 {@link ChatModelContentObservationFilter}
 * 映射为 LangFuse KeyValue，并在映射源头按 prompt/completion 各自上限脱敏截断。</p>
 */
public class SensitiveDataObservationFilter implements ObservationFilter {

    private final int maxChars;

    /**
     * @param maxChars 高基数 tag 的统一截断上限（取各类上限中的较大值，源头已按更细上限处理）
     */
    public SensitiveDataObservationFilter(int maxChars) {
        this.maxChars = maxChars;
    }

    @Override
    public Observation.Context map(Observation.Context context) {
        for (KeyValue kv : context.getHighCardinalityKeyValues()) {
            String original = kv.getValue();
            String sanitized = SensitiveDataRedactor.redactAndTruncate(original, maxChars);
            if (!sanitized.equals(original)) {
                context.addHighCardinalityKeyValue(KeyValue.of(kv.getKey(), sanitized));
            }
        }
        return context;
    }
}
