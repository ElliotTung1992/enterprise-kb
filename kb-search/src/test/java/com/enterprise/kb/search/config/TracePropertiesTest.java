package com.enterprise.kb.search.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TracePropertiesTest {

    @Test
    void defaultsMatchTraceDesign() {
        TraceProperties properties = new TraceProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.isFullRawEnabled()).isTrue();
        assertThat(properties.getSampleRate()).isEqualTo(1.0);
        assertThat(properties.getMaxPayloadBytes()).isEqualTo(262144);
        assertThat(properties.isIncludeHistory()).isTrue();
        assertThat(properties.isIncludePrompts()).isTrue();
        assertThat(properties.isIncludeRetrievalExcerpts()).isTrue();
    }
}
