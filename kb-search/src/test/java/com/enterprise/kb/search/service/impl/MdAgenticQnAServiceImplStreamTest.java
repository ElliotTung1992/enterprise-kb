package com.enterprise.kb.search.service.impl;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.enterprise.kb.common.tracing.TracingContextHolder;
import com.enterprise.kb.search.ai.RedisChatMemory;
import com.enterprise.kb.search.dto.AgentStreamEvent;
import com.enterprise.kb.search.dto.QnARequest;
import com.enterprise.kb.search.tracing.AiStreamTracingSupport;
import com.enterprise.kb.search.service.QaChatSessionService;
import org.springframework.ai.chat.messages.Message;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.messages.AssistantMessage;
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
                null, null, null, memory, session, null, null, null, null, ObservationRegistry.create(), null);
    }

    @SuppressWarnings("unchecked")
    private StreamingOutput<Object> streaming(String chunk, OutputType type) {
        StreamingOutput<Object> so = mock(StreamingOutput.class);
        when(so.getOutputType()).thenReturn(type);
        when(so.chunk()).thenReturn(chunk);
        return so;
    }

    @Test
    void mapToEventStreamEmitsAnswerEventsAndDone() {
        MdAgenticQnAServiceImpl service = newService(null, null);
        // 最终答复轮的 token（无 <think> 标签 → 全部走 answer 事件）
        StreamingOutput<Object> answer1 = streaming("Hello", OutputType.AGENT_MODEL_STREAMING);
        StreamingOutput<Object> answer2 = streaming(" world", OutputType.AGENT_MODEL_STREAMING);
        // 工具决策轮：文本为空，应被丢弃
        StreamingOutput<Object> emptyModel = streaming("", OutputType.AGENT_MODEL_STREAMING);
        // 工具执行结束：acc 无工具记录 → 不产 tool_result
        StreamingOutput<Object> toolFinished = streaming("tool-result", OutputType.AGENT_TOOL_FINISHED);
        // 非 StreamingOutput 的图节点输出：应被排除
        NodeOutput plainNode = mock(NodeOutput.class);

        Flux<NodeOutput> outputs = Flux.just(answer1, emptyModel, toolFinished, plainNode, answer2);
        MdAgenticQnAServiceImpl.MdAccumulator acc = new MdAgenticQnAServiceImpl.MdAccumulator();

        @SuppressWarnings("unchecked")
        Flux<String> events = (Flux<String>) ReflectionTestUtils.invokeMethod(
                service, "mapToEventStream", outputs, acc);
        List<String> emitted = events.collectList().block(Duration.ofSeconds(1));

        assertThat(emitted).containsExactly(
                AgentStreamEvent.answer("Hello"),
                AgentStreamEvent.answer(" world"),
                AgentStreamEvent.done());
    }

    @Test
    void mapToEventStreamEmitsFullProcessSequence() {
        MdAgenticQnAServiceImpl service = newService(null, null);

        // 1) 模型决策轮的思考增量（含 <think> 内联标签）
        StreamingOutput<Object> think = streaming("<think>需要查退款</think>", OutputType.AGENT_MODEL_STREAMING);
        // 2) 模型决策轮结束：携带一个 searchKnowledgeBase 工具调用
        AssistantMessage decision = mock(AssistantMessage.class);
        when(decision.hasToolCalls()).thenReturn(true);
        when(decision.getToolCalls()).thenReturn(List.of(
                new AssistantMessage.ToolCall("c1", "function", "searchKnowledgeBase", "{\"query\":\"退款政策\"}")));
        @SuppressWarnings("unchecked")
        StreamingOutput<Object> modelFinished = mock(StreamingOutput.class);
        when(modelFinished.getOutputType()).thenReturn(OutputType.AGENT_MODEL_FINISHED);
        when(modelFinished.message()).thenReturn(decision);
        // 3) 工具执行结束（命中数从 acc 工具记录读）
        StreamingOutput<Object> toolFinished = streaming(null, OutputType.AGENT_TOOL_FINISHED);
        // 4) 最终答复轮
        StreamingOutput<Object> answer = streaming("根据知识库，7天内可退款", OutputType.AGENT_MODEL_STREAMING);

        // acc 预置一条工具记录，模拟 searchKnowledgeBase 已执行、命中 5 条
        MdAgenticQnAServiceImpl.MdAccumulator acc = new MdAgenticQnAServiceImpl.MdAccumulator();
        acc.recordSearch("ok", 5);

        Flux<NodeOutput> outputs = Flux.just(think, modelFinished, toolFinished, answer);
        @SuppressWarnings("unchecked")
        Flux<String> events = (Flux<String>) ReflectionTestUtils.invokeMethod(
                service, "mapToEventStream", outputs, acc);
        List<String> emitted = events.collectList().block(Duration.ofSeconds(1));

        Map<String, Object> toolCall = AgentStreamEvent.event("tool_call");
        toolCall.put("tool", "searchKnowledgeBase");
        toolCall.put("query", "退款政策");
        Map<String, Object> toolResult = AgentStreamEvent.event("tool_result");
        toolResult.put("tool", "searchKnowledgeBase");
        toolResult.put("status", "ok");
        toolResult.put("hits", 5);

        assertThat(emitted).containsExactly(
                AgentStreamEvent.thinking("需要查退款"),
                AgentStreamEvent.json(toolCall),
                AgentStreamEvent.json(toolResult),
                AgentStreamEvent.answer("根据知识库，7天内可退款"),
                AgentStreamEvent.done());
    }

    @Test
    void mapToEventStreamEmitsErrorEventThenPropagatesError() {
        MdAgenticQnAServiceImpl service = newService(null, null);
        // 模型先吐了半截答案，随后上游失败
        StreamingOutput<Object> partial = streaming("半截答案", OutputType.AGENT_MODEL_STREAMING);
        Flux<NodeOutput> outputs = Flux.concat(Flux.just(partial), Flux.error(new RuntimeException("boom")));
        MdAgenticQnAServiceImpl.MdAccumulator acc = new MdAgenticQnAServiceImpl.MdAccumulator();

        @SuppressWarnings("unchecked")
        Flux<String> events = (Flux<String>) ReflectionTestUtils.invokeMethod(
                service, "mapToEventStream", outputs, acc);

        List<String> collected = new java.util.ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        events.subscribe(collected::add, error::set);

        // 前端拿到 answer + error 两个事件，但流以异常终止（无 done）→ 上游不会 doOnComplete 持久化半截答案
        assertThat(collected).containsExactly(
                AgentStreamEvent.answer("半截答案"),
                AgentStreamEvent.error("流式处理失败"));
        assertThat(error.get()).isInstanceOf(RuntimeException.class).hasMessage("boom");
    }

    @Test
    void buildStreamMessagesInjectsThinkSwitchOnlyForLlamaCpp() {
        MdAgenticQnAServiceImpl service = newService(null, null);
        // 默认 provider(null) + 关 → /no_think
        assertThat(lastUserText(service, new QnARequest("退款", null, null, null, 5, false)))
                .isEqualTo("退款 /no_think");
        // 显式 llama.cpp + 开 → /think
        assertThat(lastUserText(service, new QnARequest("退款", null, "LLAMA_CPP", null, 5, true)))
                .isEqualTo("退款 /think");
        // DashScope 不识别软开关 → 不注入，避免字面量泄漏
        assertThat(lastUserText(service, new QnARequest("退款", null, "DASHSCOPE", null, 5, true)))
                .isEqualTo("退款");
        // 默认 provider 可配置；若默认不是 LLAMA_CPP，空 provider 也不能注入 Qwen3 私有软开关
        ReflectionTestUtils.setField(service, "defaultChatProvider", "DASHSCOPE");
        assertThat(lastUserText(service, new QnARequest("退款", null, null, null, 5, true)))
                .isEqualTo("退款");
    }

    private String lastUserText(MdAgenticQnAServiceImpl service, QnARequest req) {
        @SuppressWarnings("unchecked")
        List<Message> messages = (List<Message>) ReflectionTestUtils.invokeMethod(
                service, "buildStreamMessages", new java.util.ArrayList<Message>(), req);
        return messages.get(messages.size() - 1).getText();
    }

    @Test
    void thinkStreamSplitterRoutesThinkToThinkingAndRestToAnswer() {
        MdAgenticQnAServiceImpl.ThinkStreamSplitter splitter = new MdAgenticQnAServiceImpl.ThinkStreamSplitter();
        // 标签被流式切断也应正确拼接：分两个 chunk 喂入
        List<String> emitted = new java.util.ArrayList<>();
        emitted.addAll(splitter.accept("<thi"));
        emitted.addAll(splitter.accept("nk>推理</think>正文"));
        emitted.addAll(splitter.flush());

        assertThat(emitted).containsExactly(
                AgentStreamEvent.thinking("推理"),
                AgentStreamEvent.answer("正文"));
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
