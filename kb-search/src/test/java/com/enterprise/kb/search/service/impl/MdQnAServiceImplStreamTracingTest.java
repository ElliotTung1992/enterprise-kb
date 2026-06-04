package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.tracing.TracingContextHolder;
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
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

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
