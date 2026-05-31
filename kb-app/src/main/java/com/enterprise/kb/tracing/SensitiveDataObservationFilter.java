package com.enterprise.kb.tracing;

import com.enterprise.kb.common.tracing.SensitiveDataRedactor;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;

/**
 * 进 trace 高基数 tag 的脱敏 + 截断兜底过滤器（ADR-015 D5）。
 *
 * <p>对所有 observation 的<b>高基数</b> KeyValue（正文类内容多挂在此）做正则脱敏 + 长度截断，
 * 作为统一安全网。埋点源头（tool 拦截器 / 检索 span）已按各自上限脱敏截断；本过滤器再兜一道，
 * 并覆盖框架自身写入的高基数 tag。</p>
 *
 * <p><b>已知边界（待验证风险）：</b>Spring AI 在 tracer 在场时把 prompt/completion 正文写成
 * span <b>event</b> 而非 KeyValue，{@link ObservationFilter} 作用于 KeyValue，<b>不</b>覆盖 event。
 * 该部分正文的脱敏需在 event→attribute 映射方案确定后补充自定义 SpanProcessor 处理。</p>
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
