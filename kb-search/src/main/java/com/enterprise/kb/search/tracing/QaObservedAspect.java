package com.enterprise.kb.search.tracing;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.tracing.SensitiveDataRedactor;
import com.enterprise.kb.common.tracing.TracingAttributes;
import com.enterprise.kb.common.tracing.TracingContextHolder;
import com.enterprise.kb.common.util.SecurityUtils;
import com.enterprise.kb.search.dto.AgentStreamEvent;
import com.enterprise.kb.search.dto.QnARequest;
import com.enterprise.kb.search.dto.QnAResponse;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 问答 Controller 方法 AOP：把业务根 span 收口到「请求进来的那道门」（ADR-015 根 span 边界化）。
 *
 * <p>根 span 是横切关注点，其生命周期应等于一次入站请求的生命周期，不应散落在 Service 里。本 aspect 环绕
 * 标注了 {@link QaObserved} 的 Controller 方法，统一：抽取业务身份（user/session/space/provider）、写 trace
 * input/output、按返回类型决定根 span 何时关闭。</p>
 *
 * <ul>
 *   <li><b>同步</b>（方法返回非 {@code Flux}）：{@code proceed()} 期间打开 scope，方法返回后从返回值
 *       写 output 并 {@code stop()}——「方法返回 = 请求完成」。</li>
 *   <li><b>流式</b>（方法返回 {@code Flux}）：根 span 的所有权交给 {@code Flux} 的终止信号
 *       （{@link AiStreamTracingSupport}），{@code proceed()} 只构建冷流，真正的检索/模型调用在订阅时
 *       才在 scope 内发生。</li>
 * </ul>
 *
 * <p>仅在 {@code enterprise.kb.tracing.enabled=true} 时装配；关闭时本 aspect 不存在，Controller 方法照常
 * 执行（Service 内 sessionId fallback 仍生效），零 observation 开销。</p>
 */
@Aspect
@Component
@ConditionalOnProperty(prefix = "enterprise.kb.tracing", name = "enabled", havingValue = "true")
public class QaObservedAspect {

    private final ObservationRegistry observationRegistry;
    private final int maxPromptChars;
    private final int maxCompletionChars;

    public QaObservedAspect(ObservationRegistry observationRegistry,
                            @Value("${enterprise.kb.tracing.max-prompt-chars:8000}") int maxPromptChars,
                            @Value("${enterprise.kb.tracing.max-completion-chars:8000}") int maxCompletionChars) {
        this.observationRegistry = observationRegistry;
        this.maxPromptChars = maxPromptChars;
        this.maxCompletionChars = maxCompletionChars;
    }

    /**
     * 环绕标注了 {@link QaObserved} 的 Controller 方法，在边界创建并管理业务根 span。
     *
     * @param pjp        连接点
     * @param qaObserved 注解（提供 span 名）
     * @return 方法返回值（同步结果或被根 span 接管的 token 流）
     * @throws Throwable 业务异常原样抛出（已记录到根 span）
     */
    @Around("@annotation(qaObserved)")
    public Object observe(ProceedingJoinPoint pjp, QaObserved qaObserved) throws Throwable {
        Object[] args = pjp.getArgs();
        QnARequest req = findRequest(args);
        UUID spaceId = findSpaceId(args);
        if (req == null) {
            // 非预期签名：不强行 tracing，直接放行
            return pjp.proceed();
        }
        // sessionId 单一来源：边界生成非空 sessionId，重写 arg 后传给 Service，使根 span 与会话身份一致
        UUID sessionId = req.sessionId() != null ? req.sessionId() : UUID.randomUUID();
        Object[] proceedArgs = args;
        if (req.sessionId() == null) {
            proceedArgs = withSessionId(args, req, sessionId);
        }
        String provider = req.modelProvider() != null ? req.modelProvider() : "DEFAULT";
        Map<String, String> attrs = businessAttrs(currentUserIdQuietly(), sessionId, spaceId, provider);

        MethodSignature signature = (MethodSignature) pjp.getSignature();
        boolean streaming = Flux.class.isAssignableFrom(signature.getReturnType());
        return streaming
                ? observeStream(pjp, proceedArgs, qaObserved.name(), attrs, req.question(), qaObserved.eventStream())
                : observeSync(pjp, proceedArgs, qaObserved.name(), attrs, req.question());
    }

    /**
     * 同步路径：scope 覆盖 {@code proceed()}，方法返回后写 trace output 并停止根 span。
     */
    private Object observeSync(ProceedingJoinPoint pjp, Object[] args, String name,
                               Map<String, String> attrs, String question) throws Throwable {
        AiStreamTracingSupport.TraceState state = AiStreamTracingSupport.newState(
                name, observationRegistry, attrs, question, maxPromptChars, maxCompletionChars);
        Observation observation = state.observation();
        observation.start();
        Map<String, String> previous = TracingContextHolder.peek();
        TracingContextHolder.set(attrs);
        Observation.Scope scope = observation.openScope();
        try {
            Object result = pjp.proceed(args);
            writeOutput(observation, resolveSyncOutput(result));
            return result;
        } catch (Throwable error) {
            observation.error(error);
            throw error;
        } finally {
            scope.close();
            TracingContextHolder.set(previous);
            observation.stop();
        }
    }

    /**
     * 流式路径：{@code proceed()} 仅构建冷流，根 span 由 {@link AiStreamTracingSupport} 在订阅/终止时管理。
     */
    @SuppressWarnings("unchecked")
    private Object observeStream(ProceedingJoinPoint pjp, Object[] args, String name,
                                 Map<String, String> attrs, String question, boolean eventStream) throws Throwable {
        // 端点显式声明是否「过程事件 JSON 流」：是则只把 answer 正文计入 trace output（thinking/工具事件不计），
        // 否则 identity 原样累加裸文本 token。tracing 不再猜测元素是否为 JSON，避免误删标准流里恰为 JSON 的答案。
        Function<String, String> answerExtractor = eventStream
                ? AgentStreamEvent::answerDelta : Function.identity();
        AiStreamTracingSupport.TraceState state = AiStreamTracingSupport.newState(
                name, observationRegistry, attrs, question, maxPromptChars, maxCompletionChars, answerExtractor);
        Observation observation = state.observation();
        observation.start();
        Map<String, String> previous = TracingContextHolder.peek();
        TracingContextHolder.set(attrs);
        Observation.Scope scope = observation.openScope();
        try {
            Flux<String> business = (Flux<String>) pjp.proceed(args);
            return AiStreamTracingSupport.traceStarted(state, () -> business);
        } catch (Throwable error) {
            observation.error(error);
            observation.lowCardinalityKeyValue(TracingAttributes.TRACE_METADATA_PREFIX + "status", "ERROR");
            observation.stop();
            throw error;
        } finally {
            scope.close();
            TracingContextHolder.set(previous);
        }
    }

    /**
     * 解包同步返回值得到答案正文：{@code ResponseEntity → ApiResponse → QnAResponse#answer}。
     */
    private String resolveSyncOutput(Object result) {
        Object body = result instanceof ResponseEntity<?> entity ? entity.getBody() : result;
        Object data = body instanceof ApiResponse<?> api ? api.getData() : body;
        return data instanceof QnAResponse qna ? qna.answer() : null;
    }

    private void writeOutput(Observation observation, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String sanitized = SensitiveDataRedactor.redactAndTruncate(text, maxCompletionChars);
        observation.highCardinalityKeyValue(TracingAttributes.TRACE_OUTPUT, sanitized);
        observation.highCardinalityKeyValue(TracingAttributes.OBSERVATION_OUTPUT, sanitized);
    }

    private Map<String, String> businessAttrs(String userId, UUID sessionId, UUID spaceId, String provider) {
        return AiStreamTracingSupport.businessAttrs(userId, sessionId, spaceId, provider);
    }

    private QnARequest findRequest(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof QnARequest req) {
                return req;
            }
        }
        return null;
    }

    private UUID findSpaceId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof UUID id) {
                return id;
            }
        }
        return null;
    }

    private Object[] withSessionId(Object[] args, QnARequest req, UUID sessionId) {
        Object[] copy = args.clone();
        for (int i = 0; i < copy.length; i++) {
            if (copy[i] instanceof QnARequest) {
                copy[i] = new QnARequest(req.question(), sessionId, req.modelProvider(),
                        req.modelName(), req.topK(), req.deepThinking());
                break;
            }
        }
        return copy;
    }

    /**
     * 获取当前用户 ID，无认证上下文时静默返回 {@code null}（仅用于 trace 维度，不影响业务）。
     */
    private String currentUserIdQuietly() {
        try {
            UUID userId = SecurityUtils.getCurrentUserId();
            return userId != null ? userId.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
