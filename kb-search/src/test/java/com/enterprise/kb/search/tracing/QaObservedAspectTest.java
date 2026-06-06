package com.enterprise.kb.search.tracing;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.tracing.TracingAttributes;
import com.enterprise.kb.common.tracing.TracingContextHolder;
import com.enterprise.kb.search.dto.AgentStreamEvent;
import com.enterprise.kb.search.dto.QnARequest;
import com.enterprise.kb.search.dto.QnAResponse;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QaObservedAspectTest {

    private final QaObservedAspect aspect = new QaObservedAspect(ObservationRegistry.create(), 8000, 8000);

    @BeforeEach
    @AfterEach
    void clearHolder() {
        TracingContextHolder.clear();
    }

    private QaObserved qaObserved(String name) {
        return qaObserved(name, false);
    }

    private QaObserved qaObserved(String name, boolean eventStream) {
        return new QaObserved() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return QaObserved.class;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean eventStream() {
                return eventStream;
            }
        };
    }

    private ProceedingJoinPoint joinPoint(Object[] args, Class<?> returnType,
                                          AtomicReference<Object[]> capturedArgs, Object result) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getReturnType()).thenReturn((Class) returnType);
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(args);
        when(pjp.proceed(any(Object[].class))).thenAnswer(invocation -> {
            capturedArgs.set(invocation.getArgument(0));
            return result;
        });
        return pjp;
    }

    @Test
    void syncPathInjectsGeneratedSessionIdAndReturnsResult() throws Throwable {
        UUID spaceId = UUID.randomUUID();
        QnARequest req = new QnARequest("退款流程？", null, "DASHSCOPE", null, 5);
        ResponseEntity<ApiResponse<QnAResponse>> entity = ResponseEntity.ok(ApiResponse.ok(
                new QnAResponse("请提交工单", UUID.randomUUID(), List.of(), "DASHSCOPE", 0)));
        AtomicReference<Object[]> captured = new AtomicReference<>();
        ProceedingJoinPoint pjp = joinPoint(new Object[]{spaceId, req}, ResponseEntity.class, captured, entity);

        Object result = aspect.observe(pjp, qaObserved("kb.qa.ask"));

        assertThat(result).isSameAs(entity);
        QnARequest passed = (QnARequest) captured.get()[1];
        // sessionId 在边界生成并重写 arg 传给 Service
        assertThat(passed.sessionId()).isNotNull();
        assertThat(passed.question()).isEqualTo("退款流程？");
        // holder 现场已复位，不污染线程
        assertThat(TracingContextHolder.peek()).isNull();
    }

    @Test
    void syncPathPreservesProvidedSessionId() throws Throwable {
        UUID spaceId = UUID.randomUUID();
        UUID provided = UUID.randomUUID();
        QnARequest req = new QnARequest("q", provided, "DASHSCOPE", null, 5);
        AtomicReference<Object[]> captured = new AtomicReference<>();
        ProceedingJoinPoint pjp = joinPoint(new Object[]{spaceId, req}, ResponseEntity.class, captured,
                ResponseEntity.ok(ApiResponse.ok(new QnAResponse("a", provided, List.of(), "DASHSCOPE", 0))));

        aspect.observe(pjp, qaObserved("kb.qa.ask"));

        QnARequest passed = (QnARequest) captured.get()[1];
        assertThat(passed.sessionId()).isEqualTo(provided);
        // 已有 sessionId 时不重写 arg
        assertThat(passed).isSameAs(req);
    }

    @Test
    void streamPathReturnsTokenFluxAndCleansHolder() throws Throwable {
        UUID spaceId = UUID.randomUUID();
        QnARequest req = new QnARequest("q", null, "DASHSCOPE", null, 5);
        AtomicReference<Object[]> captured = new AtomicReference<>();
        ProceedingJoinPoint pjp = joinPoint(new Object[]{spaceId, req}, Flux.class, captured,
                Flux.just("Hello", " world"));

        Object result = aspect.observe(pjp, qaObserved("kb.qa.ask.stream"));

        assertThat(result).isInstanceOf(Flux.class);
        @SuppressWarnings("unchecked")
        List<String> chunks = ((Flux<String>) result).collectList().block(Duration.ofSeconds(1));
        assertThat(chunks).containsExactly("Hello", " world");
        assertThat(TracingContextHolder.peek()).isNull();
    }

    @Test
    void streamPathInjectsGeneratedSessionId() throws Throwable {
        UUID spaceId = UUID.randomUUID();
        QnARequest req = new QnARequest("q", null, "DASHSCOPE", null, 5);
        AtomicReference<Object[]> captured = new AtomicReference<>();
        ProceedingJoinPoint pjp = joinPoint(new Object[]{spaceId, req}, Flux.class, captured, Flux.just("ok"));

        Object result = aspect.observe(pjp, qaObserved("kb.qa.ask.stream"));
        ((Flux<String>) result).collectList().block(Duration.ofSeconds(1));

        QnARequest passed = (QnARequest) captured.get()[1];
        assertThat(passed.sessionId()).isNotNull();
    }

    @Test
    void streamPathInjectsGeneratedSessionIdAndPreservesDeepThinking() throws Throwable {
        UUID spaceId = UUID.randomUUID();
        QnARequest req = new QnARequest("q", null, "LLAMA_CPP", null, 5, true);
        AtomicReference<Object[]> captured = new AtomicReference<>();
        ProceedingJoinPoint pjp = joinPoint(new Object[]{spaceId, req}, Flux.class, captured, Flux.just("ok"));

        Object result = aspect.observe(pjp, qaObserved("kb.qa.ask.agentic.stream", true));
        ((Flux<String>) result).collectList().block(Duration.ofSeconds(1));

        QnARequest passed = (QnARequest) captured.get()[1];
        assertThat(passed.sessionId()).isNotNull();
        assertThat(passed.deepThinking()).isTrue();
    }

    @Test
    void streamPathProceedRunsInsideRootScope() throws Throwable {
        UUID spaceId = UUID.randomUUID();
        QnARequest req = new QnARequest("q", null, "DASHSCOPE", null, 5);
        AtomicReference<Map<String, String>> holderInProceed = new AtomicReference<>();
        AtomicReference<Object[]> captured = new AtomicReference<>();
        ProceedingJoinPoint pjp = streamJoinPoint(new Object[]{spaceId, req}, captured, invocation -> {
            holderInProceed.set(TracingContextHolder.peek());
            return Flux.just("ok");
        });

        Object result = aspect.observe(pjp, qaObserved("kb.qa.ask.stream"));
        ((Flux<String>) result).collectList().block(Duration.ofSeconds(1));

        assertThat(holderInProceed.get()).isNotNull();
        assertThat(holderInProceed.get()).containsKey(TracingAttributes.LANGFUSE_SESSION_ID);
        assertThat(TracingContextHolder.peek()).isNull();
    }

    @Test
    void streamPathStopsObservationWhenProceedThrows() throws Throwable {
        ObservationRegistry registry = ObservationRegistry.create();
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            public void onError(Observation.Context context) {
                errors.incrementAndGet();
            }

            @Override
            public void onStop(Observation.Context context) {
                stops.incrementAndGet();
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        });
        QaObservedAspect localAspect = new QaObservedAspect(registry, 8000, 8000);
        UUID spaceId = UUID.randomUUID();
        QnARequest req = new QnARequest("q", null, "DASHSCOPE", null, 5);
        IllegalStateException failure = new IllegalStateException("boom");
        ProceedingJoinPoint pjp = streamJoinPoint(new Object[]{spaceId, req}, new AtomicReference<>(), invocation -> {
            throw failure;
        });

        assertThatThrownBy(() -> localAspect.observe(pjp, qaObserved("kb.qa.ask.stream")))
                .isSameAs(failure);

        assertThat(errors).hasValue(1);
        assertThat(stops).hasValue(1);
        assertThat(TracingContextHolder.peek()).isNull();
    }

    @Test
    void streamTracePreservesJsonAnswerWhenNotEventStream() throws Throwable {
        // P3 回归：标准流（eventStream=false）用 identity 抽取器，恰为 JSON 的答案 token 原样进 trace output，不被误删
        String output = captureStreamTraceOutput("kb.qa.ask.stream", false,
                Flux.just("{\"type\":\"invoice\"}"));
        assertThat(output).isEqualTo("{\"type\":\"invoice\"}");
    }

    @Test
    void streamTraceExtractsOnlyAnswerWhenEventStream() throws Throwable {
        // 事件流（eventStream=true）：只 answer 正文计入 trace output，thinking/done 不计
        String output = captureStreamTraceOutput("kb.qa.ask.agentic.stream", true,
                Flux.just(AgentStreamEvent.thinking("想"), AgentStreamEvent.answer("答案"), AgentStreamEvent.done()));
        assertThat(output).isEqualTo("答案");
    }

    @SuppressWarnings("unchecked")
    private String captureStreamTraceOutput(String name, boolean eventStream, Flux<String> business) throws Throwable {
        ObservationRegistry registry = ObservationRegistry.create();
        AtomicReference<String> output = new AtomicReference<>();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            public void onStop(Observation.Context context) {
                context.getHighCardinalityKeyValues().stream()
                        .filter(kv -> kv.getKey().equals(TracingAttributes.TRACE_OUTPUT))
                        .findFirst().ifPresent(kv -> output.set(kv.getValue()));
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        });
        QaObservedAspect localAspect = new QaObservedAspect(registry, 8000, 8000);
        QnARequest req = new QnARequest("q", null, "DASHSCOPE", null, 5);
        ProceedingJoinPoint pjp = streamJoinPoint(new Object[]{UUID.randomUUID(), req},
                new AtomicReference<>(), invocation -> business);
        Object result = localAspect.observe(pjp, qaObserved(name, eventStream));
        ((Flux<String>) result).collectList().block(Duration.ofSeconds(1));
        return output.get();
    }

    private ProceedingJoinPoint streamJoinPoint(Object[] args, AtomicReference<Object[]> capturedArgs,
                                                org.mockito.stubbing.Answer<Object> answer) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getReturnType()).thenReturn((Class) Flux.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(args);
        when(pjp.proceed(any(Object[].class))).thenAnswer(invocation -> {
            capturedArgs.set(invocation.getArgument(0));
            return answer.answer(invocation);
        });
        return pjp;
    }
}
