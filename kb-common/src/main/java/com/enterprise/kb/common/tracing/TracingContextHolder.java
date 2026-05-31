package com.enterprise.kb.common.tracing;

import io.micrometer.context.ContextRegistry;

import java.util.Collections;
import java.util.Map;

/**
 * 当前 trace 的业务属性持有器（ADR-015 跨线程传播 D4）。
 *
 * <p>service 入口的业务根 span 启动时，把 {@code langfuse.user.id} / {@code langfuse.session.id} /
 * metadata 等业务属性写入本 holder；{@code LangfuseChildAttributeSpanProcessor} 在每个子 span
 * {@code onStart} 时读取 holder，把这些属性补到子 span 上。</p>
 *
 * <p>底层是 {@link ThreadLocal}，并向 context-propagation 的全局 {@link ContextRegistry} 注册了
 * {@link ThreadLocalAccessor}，因此：</p>
 * <ul>
 *   <li>① reactor 自动传播（{@code Hooks.enableAutomaticContextPropagation()}）会顺着 reactor
 *       context 把它带进 graph-core 内部线程（覆盖 LLM span）；</li>
 *   <li>② {@code ContextSnapshot.captureAll()} 包装的 executor 会把它带进 {@code CompletableFuture}
 *       并行检索线程（覆盖 ForkJoinPool）。</li>
 * </ul>
 *
 * <p>注意：本 holder 走的是<b>本地 thread-local</b>，不是 OTel baggage，因此业务属性<b>不会</b>随
 * 下游 HTTP 请求透传给模型提供商。</p>
 */
public final class TracingContextHolder {

    /** context-propagation 注册 key。 */
    public static final String KEY = "enterprise.kb.tracing.context";

    private static final ThreadLocal<Map<String, String>> HOLDER = new ThreadLocal<>();

    static {
        // 注册到全局 ContextRegistry，使 ContextSnapshot / reactor 自动传播能搬运本 holder。
        ContextRegistry.getInstance().registerThreadLocalAccessor(KEY, HOLDER);
    }

    private TracingContextHolder() {
    }

    /**
     * 读取当前线程的业务属性（只读视图，不存在时返回空 Map）。
     *
     * @return 业务属性 Map，永不为 {@code null}
     */
    public static Map<String, String> current() {
        Map<String, String> value = HOLDER.get();
        return value == null ? Collections.emptyMap() : value;
    }

    /**
     * 读取当前线程的原始业务属性（可能为 {@code null}），用于保存/恢复现场。
     *
     * @return 业务属性 Map，可能为 {@code null}
     */
    public static Map<String, String> peek() {
        return HOLDER.get();
    }

    /**
     * 设置当前线程的业务属性。
     *
     * @param attributes 业务属性 Map，{@code null} 表示清空
     */
    public static void set(Map<String, String> attributes) {
        if (attributes == null) {
            HOLDER.remove();
        } else {
            HOLDER.set(attributes);
        }
    }

    /** 清空当前线程的业务属性。 */
    public static void clear() {
        HOLDER.remove();
    }
}
