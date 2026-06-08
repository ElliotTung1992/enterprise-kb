package com.enterprise.kb.common.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalPromptProviderTest {

    @Test
    void getRendersClasspathFallback() {
        LocalPromptProvider provider = new LocalPromptProvider(new LocalPromptStore(), new MustacheLite());

        RenderedPrompt prompt = provider.get("test/simple", Map.of("name", "Alice", "empty", ""));

        assertThat(prompt.name()).isEqualTo("test/simple");
        assertThat(prompt.version()).isZero();
        assertThat(prompt.text()).isEqualTo("Hello Alice\nOptional \n");
    }

    @Test
    void localStoreRejectsPathTraversalPromptName() {
        LocalPromptStore store = new LocalPromptStore();

        assertThatThrownBy(() -> store.fetch("../application"))
                .isInstanceOf(PromptRenderException.class)
                .hasMessageContaining("非法 prompt 名称");
    }
}
