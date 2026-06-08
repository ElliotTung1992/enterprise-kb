package com.enterprise.kb.common.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MustacheLiteTest {

    private final MustacheLite mustacheLite = new MustacheLite();

    @Test
    void renderReplacesNamedVariables() {
        String result = mustacheLite.render("Hello {{ name }}", Map.of("name", "Alice"));

        assertThat(result).isEqualTo("Hello Alice");
    }

    @Test
    void renderAllowsEmptyString() {
        String result = mustacheLite.render("A{{optional}}B", Map.of("optional", ""));

        assertThat(result).isEqualTo("AB");
    }

    @Test
    void renderFailsWhenVariableMissing() {
        assertThatThrownBy(() -> mustacheLite.render("Hello {{name}}", Map.of()))
                .isInstanceOf(PromptRenderException.class)
                .hasMessageContaining("name");
    }
}
