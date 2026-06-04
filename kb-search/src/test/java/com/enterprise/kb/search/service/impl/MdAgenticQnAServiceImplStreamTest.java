package com.enterprise.kb.search.service.impl;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.enterprise.kb.common.tracing.TracingContextHolder;
import com.enterprise.kb.search.ai.RedisChatMemory;
import com.enterprise.kb.search.tracing.AiStreamTracingSupport;
import com.enterprise.kb.search.service.QaChatSessionService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MdAgenticQnAServiceImpl 流式问答关键逻辑的单元测试。
 */
class MdAgenticQnAServiceImplStreamTest {

    // TracingContextHolder 是 thread-local，跨测试类共用 main 线程时可能被前序测试污染。
    @BeforeEach
    @AfterEach
    void clearTracingHolder() {
        TracingContextHolder.clear();
    }

    private MdAgenticQnAServiceImpl newService(RedisChatMemory memory, QaChatSessionService session) {
        return new MdAgenticQnAServiceImpl(
                null, null, null, memory, session, null, null, null, null, ObservationRegistry.create());
    }

    @SuppressWarnings("unchecked")
    private StreamingOutput<Object> streaming(String chunk, OutputType type) {
        StreamingOutput<Object> so = mock(StreamingOutput.class);
        when(so.getOutputType()).thenReturn(type);
        when(so.chunk()).thenReturn(chunk);
        return so;
    }

    @Test
    void extractAnswerTokensKeepsOnlyModelStreamingNonEmptyChunks() {
        MdAgenticQnAServiceImpl service = newService(null, null);
        // 最终答复轮的 token
        StreamingOutput<Object> answer1 = streaming("Hello", OutputType.AGENT_MODEL_STREAMING);
        StreamingOutput<Object> answer2 = streaming(" world", OutputType.AGENT_MODEL_STREAMING);
        // 工具决策轮：文本为空，应被丢弃
        StreamingOutput<Object> emptyModel = streaming("", OutputType.AGENT_MODEL_STREAMING);
        // 工具结果输出：非模型流，应被排除
        StreamingOutput<Object> toolFinished = streaming("tool-result", OutputType.AGENT_TOOL_FINISHED);
        // 非 StreamingOutput 的图节点输出：应被排除
        NodeOutput plainNode = mock(NodeOutput.class);

        Flux<NodeOutput> outputs = Flux.just(answer1, emptyModel, toolFinished, plainNode, answer2);

        @SuppressWarnings("unchecked")
        Flux<String> tokens = (Flux<String>) ReflectionTestUtils.invokeMethod(
                service, "extractAnswerTokens", outputs);
        List<String> chunks = tokens.collectList().block(Duration.ofSeconds(1));

        assertThat(chunks).containsExactly("Hello", " world");
    }

    @Test
    void streamInTraceScopePropagatesParentObservationAndCleansHolder() {
        Observation parent = Observation
                .createNotStarted("kb.qa.ask.agentic.stream", ObservationRegistry.create()).start();
        Map<String, String> attrs = Map.of("space_id", "space-1");
        AtomicReference<Object> observedParent = new AtomicReference<>();
        AtomicReference<Object> observedAttrs = new AtomicReference<>();

        Supplier<Flux<String>> sourceFactory = () -> Flux.deferContextual(ctx -> {
            observedParent.set(ctx.getOrDefault(ObservationThreadLocalAccessor.KEY, null));
            observedAttrs.set(ctx.getOrDefault(TracingContextHolder.KEY, null));
            return Flux.just("ok");
        });

        Flux<String> result = AiStreamTracingSupport.inScope(sourceFactory, parent, attrs);
        List<String> chunks = result.collectList().block(Duration.ofSeconds(1));

        assertThat(chunks).containsExactly("ok");
        assertThat(observedParent.get()).isSameAs(parent);
        assertThat(observedAttrs.get()).isEqualTo(attrs);
        // scope 关闭后业务属性 thread-local 必须复位，避免污染线程
        assertThat(TracingContextHolder.peek()).isNull();
        parent.stop();
    }

    @Test
    void persistStreamExchangeWritesMemoryAndSavesSession() {
        RedisChatMemory memory = mock(RedisChatMemory.class);
        QaChatSessionService session = mock(QaChatSessionService.class);
        MdAgenticQnAServiceImpl service = newService(memory, session);
        UUID sessionId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ReflectionTestUtils.invokeMethod(service, "persistStreamExchange",
                sessionId, spaceId, userId, "问题", "答复");

        verify(memory).add(eq(sessionId.toString()), anyList());
        verify(session).saveExchange(sessionId, spaceId, userId, "问题", "答复");
    }

    @Test
    void persistStreamExchangeSwallowsSaveFailure() {
        RedisChatMemory memory = mock(RedisChatMemory.class);
        QaChatSessionService session = mock(QaChatSessionService.class);
        doThrow(new RuntimeException("db down"))
                .when(session).saveExchange(any(), any(), any(), any(), any());
        MdAgenticQnAServiceImpl service = newService(memory, session);

        // 持久化失败不得向上抛出，否则会中断已返回的流
        ReflectionTestUtils.invokeMethod(service, "persistStreamExchange",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "q", "a");

        verify(memory).add(any(), anyList());
    }
}
