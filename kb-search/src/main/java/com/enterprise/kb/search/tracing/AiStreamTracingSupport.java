package com.enterprise.kb.search.tracing;

import com.enterprise.kb.common.tracing.SensitiveDataRedactor;
import com.enterprise.kb.common.tracing.TracingAttributes;
import com.enterprise.kb.common.tracing.TracingContextHolder;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * AI 流式问答的根 span reactor 生命周期工具（ADR-015 根 span 边界化）。
 *
 * <p>流式（{@code Flux}）端点不能用同步 {@code observe}：Controller 返回 {@code Flux} 时 token 尚未产出，
 * 真正的检索/模型调用要等 Spring 订阅后才开始。因此根 span 的所有权必须从方法栈转移给流的终止信号——
 * 返回时关闭当前线程 scope（observation 不停），由 {@code doFinally} 在 complete / error / cancel
 * 时统一写状态 + 聚合 output 并 {@code stop()}，避免 observation 泄漏。</p>
 *
 * <p>本工具被 {@link QaObservedAspect} 在 Controller 方法 AOP 边界使用；业务 Service 只返回原始
 * {@code Flux<String>}，不感知根 span。</p>
 */
public final class AiStreamTracingSupport {

    private AiStreamTracingSupport() {
    }

    /**
     * 构建未 start 的流式根 span 状态：写入业务属性与 trace 级 input。
     *
     * @param observationName    span 名（如 {@code kb.qa.ask.stream}）
     * @param observationRegistry 可观测注册表
     * @param attrs              业务属性（user/session/space/provider）
     * @param input              trace 级 input（如用户问题）
     * @param maxPromptChars     input 截断上限
     * @param maxCompletionChars output 截断上限
     * @return 流式根 span 状态
     */
    public static TraceState newState(String observationName, ObservationRegistry observationRegistry,
                                      Map<String, String> attrs, String input, int maxPromptChars,
                                      int maxCompletionChars) {
        Observation observation = Observation.createNotStarted(observationName, observationRegistry);
        attrs.forEach((key, value) -> {
            if (TracingAttributes.LANGFUSE_USER_ID.equals(key) || TracingAttributes.LANGFUSE_SESSION_ID.equals(key)) {
                observation.highCardinalityKeyValue(key, value);
            } else {
                observation.lowCardinalityKeyValue(key, value);
            }
        });
        traceText(observation, input, maxPromptChars,
                TracingAttributes.TRACE_INPUT, TracingAttributes.OBSERVATION_INPUT);
        return new TraceState(observation, attrs, new StringBuilder(),
                new AtomicBoolean(false), new AtomicReference<>(), maxCompletionChars);
    }

    /**
     * 用流式根 span 包裹业务 token 流：订阅时 start，token 聚合进 output，终止信号 stop。
     *
     * @param trace         流式根 span 状态
     * @param sourceFactory 业务 token 流工厂（延迟到订阅时构建）
     * @return 接管了根 span 生命周期的 token 流
     */
    public static Flux<String> trace(TraceState trace, Supplier<Flux<String>> sourceFactory) {
        return Flux.defer(() -> {
            Observation observation = trace.observation().start();
            return traceStarted(trace, sourceFactory);
        });
    }

    /**
     * 用已 start 的流式根 span 包裹业务 token 流：token 聚合进 output，终止信号 stop。
     *
     * <p>Controller AOP 的流式分支需要先 start/open scope 覆盖 {@code proceed()}，拿到业务冷流后再调用
     * 本方法把 observation 的 stop 交给 {@code Flux} 终止信号，避免重复 start。</p>
     *
     * @param trace         已 start 的流式根 span 状态
     * @param sourceFactory 业务 token 流工厂
     * @return 接管了根 span终止生命周期的 token 流
     */
    public static Flux<String> traceStarted(TraceState trace, Supplier<Flux<String>> sourceFactory) {
        Observation observation = trace.observation();
        return inScope(sourceFactory, observation, trace.attrs())
                .doOnNext(trace.answerBuffer()::append)
                .doOnComplete(() -> trace.completed().set(true))
                .doOnError(trace.errorRef()::set)
                .doFinally(signalType -> finish(observation, trace));
    }

    /**
     * 在根 observation 的 scope 与业务属性 holder 下构建业务流，并把 observation / holder 写入
     * reactor context，使 reactor 内部线程切换后 LLM / graph / tool span 仍能找到父节点。
     *
     * @param sourceFactory 业务 token 流工厂
     * @param observation   根 observation
     * @param attrs         业务属性
     * @return 写入了 tracing 上下文的业务流
     */
    public static Flux<String> inScope(Supplier<Flux<String>> sourceFactory, Observation observation,
                                       Map<String, String> attrs) {
        return Flux.defer(() -> {
            Map<String, String> previous = TracingContextHolder.peek();
            Observation.Scope scope = observation.openScope();
            TracingContextHolder.set(attrs);
            Flux<String> source;
            try {
                source = sourceFactory.get();
            } catch (RuntimeException | Error e) {
                TracingContextHolder.set(previous);
                scope.close();
                throw e;
            }
            return source.contextWrite(ctx -> ctx
                            .put(ObservationThreadLocalAccessor.KEY, observation)
                            .put(TracingContextHolder.KEY, attrs))
                    .doFinally(signalType -> {
                        TracingContextHolder.set(previous);
                        scope.close();
                    });
        }).contextWrite(ctx -> ctx
                .put(ObservationThreadLocalAccessor.KEY, observation)
                .put(TracingContextHolder.KEY, attrs));
    }

    /**
     * 构建流式根 span 的业务属性 Map（user/session/space/provider）。
     *
     * @param userId    用户 ID（可为 {@code null}）
     * @param sessionId 会话 ID
     * @param spaceId   空间 ID
     * @param provider  模型 provider
     * @return 业务属性 Map
     */
    public static Map<String, String> businessAttrs(String userId, UUID sessionId, UUID spaceId, String provider) {
        Map<String, String> attrs = new LinkedHashMap<>();
        if (userId != null) {
            attrs.put(TracingAttributes.LANGFUSE_USER_ID, userId);
        }
        attrs.put(TracingAttributes.LANGFUSE_SESSION_ID, sessionId.toString());
        attrs.put(TracingAttributes.META_SPACE_ID, spaceId.toString());
        attrs.put(TracingAttributes.META_MODEL_PROVIDER, provider);
        return attrs;
    }

    private static void finish(Observation observation, TraceState trace) {
        Throwable error = trace.errorRef().get();
        if (error != null) {
            observation.error(error);
            observation.lowCardinalityKeyValue(TracingAttributes.TRACE_METADATA_PREFIX + "status", "ERROR");
        } else if (trace.completed().get()) {
            observation.lowCardinalityKeyValue(TracingAttributes.TRACE_METADATA_PREFIX + "status", "COMPLETED");
            traceText(observation, trace.answerBuffer().toString(), trace.maxCompletionChars(),
                    TracingAttributes.TRACE_OUTPUT, TracingAttributes.OBSERVATION_OUTPUT);
        } else {
            observation.lowCardinalityKeyValue(TracingAttributes.TRACE_METADATA_PREFIX + "status", "CANCELLED");
        }
        observation.stop();
    }

    private static void traceText(Observation observation, String text, int maxChars,
                                  String traceKey, String observationKey) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String sanitized = SensitiveDataRedactor.redactAndTruncate(text, maxChars);
        observation.highCardinalityKeyValue(traceKey, sanitized);
        observation.highCardinalityKeyValue(observationKey, sanitized);
    }

    /**
     * 流式根 span 的可变状态。
     *
     * @param observation        根 observation
     * @param attrs              业务属性
     * @param answerBuffer       聚合 token 的缓冲
     * @param completed          是否正常完成
     * @param errorRef           异常引用
     * @param maxCompletionChars output 截断上限
     */
    public record TraceState(
            Observation observation,
            Map<String, String> attrs,
            StringBuilder answerBuffer,
            AtomicBoolean completed,
            AtomicReference<Throwable> errorRef,
            int maxCompletionChars) {
    }
}
