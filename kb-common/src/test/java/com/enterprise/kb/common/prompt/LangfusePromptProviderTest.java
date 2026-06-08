package com.enterprise.kb.common.prompt;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.enterprise.kb.common.tracing.TracingAttributes;
import com.enterprise.kb.common.tracing.TracingContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LangfusePromptProviderTest {

    @AfterEach
    void clearTracingHolder() {
        TracingContextHolder.clear();
    }

    @Test
    void getFallsBackToLocalPromptWhenLangfuseFetchFails() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        LangfusePromptProvider provider = new LangfusePromptProvider(
                new FailingClient(), new LocalPromptStore(), new MustacheLite(), testProperties(),
                Optional.of(meterRegistry));

        RenderedPrompt prompt = provider.get("test/simple", Map.of("name", "Alice", "empty", ""));

        assertThat(prompt.version()).isZero();
        assertThat(prompt.text()).isEqualTo("Hello Alice\nOptional \n");
        assertThat(meterRegistry.counter("kb.prompt.fetch.failure").count()).isEqualTo(1);
    }

    @Test
    void getFallsBackToLocalPromptWhenLangfuseTemplateCannotRender() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        LangfusePromptProvider provider = new LangfusePromptProvider(
                new StaticClient(new CachedPrompt("test/simple", 7, "Hello {{name}} {{new_var}}")),
                new LocalPromptStore(), new MustacheLite(), testProperties(), Optional.of(meterRegistry));

        RenderedPrompt prompt = provider.get("test/simple", Map.of("name", "Alice", "empty", ""));

        assertThat(prompt.version()).isZero();
        assertThat(prompt.text()).isEqualTo("Hello Alice\nOptional \n");
        assertThat(meterRegistry.counter("kb.prompt.render.failure").count()).isEqualTo(1);
    }

    @Test
    void getDoesNotWritePromptVersionIntoTracingHolder() {
        TracingContextHolder.set(Map.of("space_id", "space-1"));
        LangfusePromptProvider provider = new LangfusePromptProvider(
                new StaticClient(new CachedPrompt("test/simple", 7, "Hello {{name}}")),
                new LocalPromptStore(), new MustacheLite(), testProperties(), Optional.empty());

        provider.get("test/simple", Map.of("name", "Alice"));

        assertThat(TracingContextHolder.current())
                .containsEntry("space_id", "space-1")
                .doesNotContainKey(TracingAttributes.OBSERVATION_PROMPT_NAME)
                .doesNotContainKey(TracingAttributes.OBSERVATION_PROMPT_VERSION);
    }

    @Test
    void getForTraceWritesPromptVersionIntoTracingHolder() {
        TracingContextHolder.set(Map.of("space_id", "space-1"));
        LangfusePromptProvider provider = new LangfusePromptProvider(
                new StaticClient(new CachedPrompt("test/simple", 7, "Hello {{name}}")),
                new LocalPromptStore(), new MustacheLite(), testProperties(), Optional.empty());

        provider.getForTrace("test/simple", Map.of("name", "Alice"));

        assertThat(TracingContextHolder.current())
                .containsEntry("space_id", "space-1")
                .containsEntry(TracingAttributes.OBSERVATION_PROMPT_NAME, "test/simple")
                .containsEntry(TracingAttributes.OBSERVATION_PROMPT_VERSION, "7");
    }

    private PromptProperties testProperties() {
        PromptProperties properties = new PromptProperties();
        properties.getCache().setTtl(Duration.ofSeconds(60));
        properties.getCache().setExpire(Duration.ofHours(24));
        return properties;
    }

    static class FailingClient extends LangfusePromptClient {
        FailingClient() {
            super(null, null);
        }

        @Override
        public CachedPrompt fetch(String name) {
            throw new RuntimeException("down");
        }
    }

    static class StaticClient extends LangfusePromptClient {
        private final CachedPrompt prompt;

        StaticClient(CachedPrompt prompt) {
            super(null, null);
            this.prompt = prompt;
        }

        @Override
        public CachedPrompt fetch(String name) {
            return prompt;
        }
    }
}
