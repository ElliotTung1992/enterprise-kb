package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.tracing.TracingContextHolder;
import com.enterprise.kb.search.dto.AgentStreamEvent;
import com.enterprise.kb.search.dto.QnARequest;
import com.enterprise.kb.search.service.QaChatSessionService;
import com.enterprise.kb.search.tracing.AiStreamTracingSupport;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MdQnAServiceImplStreamTracingTest {

    @BeforeEach
    @AfterEach
    void clearTracingHolder() {
        TracingContextHolder.clear();
    }

    @Test
    void streamInTraceScopePropagatesParentObservationToStreamingSourceContext() {
        Observation parent = Observation.createNotStarted("kb.qa.ask.stream", ObservationRegistry.create()).start();
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
        assertThat(TracingContextHolder.peek()).isNull();
        parent.stop();
    }

    @Test
    void chatClientStreamWithMemoryAdvisorKeepsParentObservationForModelStream() {
        Observation parent = Observation.createNotStarted("kb.qa.ask.stream", ObservationRegistry.create()).start();
        Map<String, String> attrs = Map.of("space_id", "space-1");
        AtomicReference<Object> observedParent = new AtomicReference<>();

        ChatClient chatClient = ChatClient.builder(new ContextCapturingChatModel(observedParent)).build();
        Supplier<Flux<String>> sourceFactory = () -> chatClient.prompt()
                .advisors(MessageChatMemoryAdvisor.builder(new InMemoryChatMemory())
                        .conversationId("session-1")
                        .scheduler(Schedulers.immediate())
                        .build())
                .user("hello")
                .stream()
                .content();

        Flux<String> result = AiStreamTracingSupport.inScope(sourceFactory, parent, attrs);

        List<String> chunks = result.collectList().block(Duration.ofSeconds(1));

        assertThat(chunks).containsExactly("ok");
        assertThat(observedParent.get()).isSameAs(parent);
        parent.stop();
    }

    @Test
    void mapStandardStreamToEventsRoutesThinkToThinkingAndRestToAnswer() {
        MdQnAServiceImpl service = newService(null);

        @SuppressWarnings("unchecked")
        Flux<String> events = (Flux<String>) ReflectionTestUtils.invokeMethod(
                service, "mapStandardStreamToEvents", Flux.just("<thi", "nk>推理</think>正文"));
        List<String> emitted = events.collectList().block(Duration.ofSeconds(1));

        assertThat(emitted).containsExactly(
                AgentStreamEvent.thinking("推理"),
                AgentStreamEvent.answer("正文"),
                AgentStreamEvent.done());
    }

    @Test
    void persistStreamAnswerSavesOnlyAnswerEvents() {
        QaChatSessionService session = mock(QaChatSessionService.class);
        MdQnAServiceImpl service = newService(session);
        UUID sessionId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Flux<String> source = Flux.just(
                AgentStreamEvent.thinking("内部推理"),
                AgentStreamEvent.answer("最终答案"));

        @SuppressWarnings("unchecked")
        Flux<String> events = (Flux<String>) ReflectionTestUtils.invokeMethod(
                service, "persistStreamAnswer", source, sessionId, spaceId, userId, "问题");
        List<String> emitted = events.collectList().block(Duration.ofSeconds(1));

        assertThat(emitted).containsExactly(
                AgentStreamEvent.thinking("内部推理"),
                AgentStreamEvent.answer("最终答案"));
        verify(session).saveExchange(sessionId, spaceId, userId, "问题", "最终答案");
    }

    @Test
    void streamUserQuestionInjectsThinkSwitchOnlyForLlamaCpp() {
        MdQnAServiceImpl service = newService(null);

        assertThat(streamUserQuestion(service, new QnARequest("退款", null, null, null, 5, false)))
                .isEqualTo("退款");
        assertThat(streamUserQuestion(service, new QnARequest("退款", null, "LLAMA_CPP", null, 5, true)))
                .isEqualTo("退款 /think");
        assertThat(streamUserQuestion(service, new QnARequest("退款", null, "DASHSCOPE", null, 5, true)))
                .isEqualTo("退款");

        ReflectionTestUtils.setField(service, "defaultChatProvider", "DASHSCOPE");
        assertThat(streamUserQuestion(service, new QnARequest("退款", null, null, null, 5, true)))
                .isEqualTo("退款");
    }

    private MdQnAServiceImpl newService(QaChatSessionService session) {
        return new MdQnAServiceImpl(
                null, null, null, null, null, null, null, session, ObservationRegistry.create(), null);
    }

    private String streamUserQuestion(MdQnAServiceImpl service, QnARequest req) {
        return ReflectionTestUtils.invokeMethod(service, "streamUserQuestion", req);
    }

    private record ContextCapturingChatModel(AtomicReference<Object> observedParent) implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return null;
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.deferContextual(ctx -> {
                observedParent.set(ctx.getOrDefault(ObservationThreadLocalAccessor.KEY, null));
                return Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("ok")))));
            });
        }
    }

    private static final class InMemoryChatMemory implements ChatMemory {
        private final Map<String, List<Message>> messages = new ConcurrentHashMap<>();

        @Override
        public void add(String conversationId, List<Message> messages) {
            this.messages.computeIfAbsent(conversationId, key -> new ArrayList<>()).addAll(messages);
        }

        @Override
        public List<Message> get(String conversationId) {
            return List.copyOf(messages.getOrDefault(conversationId, List.of()));
        }

        @Override
        public void clear(String conversationId) {
            messages.remove(conversationId);
        }
    }
}
