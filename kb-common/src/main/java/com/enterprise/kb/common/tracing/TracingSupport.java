package com.enterprise.kb.common.tracing;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 在线 Tracing 的命令式助手（ADR-015）。
 *
 * <p>统一封装「开 span → 设业务属性 → 运行业务 → 关 span」的样板，并在 tracing 总开关关闭时
 * 直接运行业务体（零 observation 开销）。两类用法：</p>
 *
 * <pre>{@code
 * // 业务根 span：设 LangFuse user/session/metadata，并写入 TracingContextHolder 供子 span 复制
 * TracingSupport.root(registry, enabled, "kb.qa.ask")
 *         .userId(userId).sessionId(sessionId).spaceId(spaceId).modelProvider(provider)
 *         .observe(() -> doAsk());
 *
 * // 子 span：检索漏斗 / 图片理解等，自动挂在当前 span 下
 * TracingSupport.span(registry, enabled, "kb.retrieval.rerank")
 *         .tag("kb.rerank.candidates", String.valueOf(n))
 *         .observe(() -> rerankService.rerank(...));
 * }</pre>
 *
 * <p>流式（{@code Flux}）端点不能用本助手的同步 {@code observe}，需用 reactor 生命周期模式
 * （{@code contextWrite} + subscribe 时 start + {@code doFinally} 时 stop），见调用方实现。</p>
 */
public final class TracingSupport {

    private TracingSupport() {
    }

    /**
     * 创建一个业务根 span 构建器（会设置 {@link TracingContextHolder}）。
     *
     * @param registry 可观测注册表
     * @param enabled  tracing 总开关；为 {@code false} 时直接运行业务体
     * @param name     span 名（如 {@code kb.qa.ask}）
     * @return span 构建器
     */
    public static SpanBuilder root(ObservationRegistry registry, boolean enabled, String name) {
        return new SpanBuilder(registry, enabled, name, true);
    }

    /**
     * 创建一个子 span 构建器（不改写 holder，挂在当前 span 下）。
     *
     * @param registry 可观测注册表
     * @param enabled  tracing 总开关；为 {@code false} 时直接运行业务体
     * @param name     span 名（如 {@code kb.retrieval.vector}）
     * @return span 构建器
     */
    public static SpanBuilder span(ObservationRegistry registry, boolean enabled, String name) {
        return new SpanBuilder(registry, enabled, name, false);
    }

    /**
     * span 构建器：累积属性并执行业务体。
     */
    public static final class SpanBuilder {

        private final ObservationRegistry registry;
        private final boolean enabled;
        private final String name;
        private final boolean root;
        /** 进 span 的低基数 tag。 */
        private final Map<String, String> lowCardinality = new LinkedHashMap<>();
        /** 进 span 的高基数 tag。 */
        private final Map<String, String> highCardinality = new LinkedHashMap<>();
        /** 仅 root span：写入 holder 供子 span 复制的业务属性。 */
        private final Map<String, String> holderAttributes = new LinkedHashMap<>();

        private SpanBuilder(ObservationRegistry registry, boolean enabled, String name, boolean root) {
            this.registry = registry;
            this.enabled = enabled;
            this.name = name;
            this.root = root;
        }

        /** 设置 LangFuse 用户维度（高基数，并写入 holder）。 */
        public SpanBuilder userId(Object userId) {
            return high(TracingAttributes.LANGFUSE_USER_ID, userId, true);
        }

        /** 设置 LangFuse 会话维度（高基数，并写入 holder）。 */
        public SpanBuilder sessionId(Object sessionId) {
            return high(TracingAttributes.LANGFUSE_SESSION_ID, sessionId, true);
        }

        /** 设置知识空间维度（metadata，低基数，并写入 holder）。 */
        public SpanBuilder spaceId(Object spaceId) {
            return low(TracingAttributes.META_SPACE_ID, spaceId, true);
        }

        /** 设置模型 provider 维度（metadata，低基数，并写入 holder）。 */
        public SpanBuilder modelProvider(Object provider) {
            return low(TracingAttributes.META_MODEL_PROVIDER, provider, true);
        }

        /** 设置文档维度（入库 trace 用，metadata，低基数，并写入 holder）。 */
        public SpanBuilder documentId(Object documentId) {
            return low(TracingAttributes.META_DOCUMENT_ID, documentId, true);
        }

        /** 追加一个低基数 tag（仅当前 span，不进 holder）。 */
        public SpanBuilder tag(String key, String value) {
            return low(key, value, false);
        }

        /** 追加一个高基数 tag（仅当前 span，不进 holder）。 */
        public SpanBuilder highTag(String key, String value) {
            return high(key, value, false);
        }

        private SpanBuilder high(String key, Object value, boolean toHolder) {
            if (value != null) {
                String v = value.toString();
                highCardinality.put(key, v);
                if (toHolder) {
                    holderAttributes.put(key, v);
                }
            }
            return this;
        }

        private SpanBuilder low(String key, Object value, boolean toHolder) {
            if (value != null) {
                String v = value.toString();
                lowCardinality.put(key, v);
                if (toHolder) {
                    holderAttributes.put(key, v);
                }
            }
            return this;
        }

        /**
         * 在 span 内执行有返回值的业务体。
         *
         * @param body 业务体
         * @param <T>  返回类型
         * @return 业务体返回值
         */
        public <T> T observe(Supplier<T> body) {
            if (!enabled) {
                return body.get();
            }
            Observation observation = Observation.createNotStarted(name, registry);
            lowCardinality.forEach(observation::lowCardinalityKeyValue);
            highCardinality.forEach(observation::highCardinalityKeyValue);
            if (!root) {
                return observation.observe(body);
            }
            // 根 span：把业务属性写入 holder（供子 span 复制），结束后恢复现场
            return observation.observe(() -> {
                Map<String, String> previous = TracingContextHolder.peek();
                TracingContextHolder.set(holderAttributes);
                try {
                    return body.get();
                } finally {
                    TracingContextHolder.set(previous);
                }
            });
        }

        /**
         * 在 span 内执行无返回值的业务体。
         *
         * @param body 业务体
         */
        public void observe(Runnable body) {
            observe(() -> {
                body.run();
                return null;
            });
        }
    }
}
