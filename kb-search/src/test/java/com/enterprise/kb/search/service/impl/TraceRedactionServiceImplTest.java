package com.enterprise.kb.search.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceRedactionServiceImplTest {

    private final TraceRedactionServiceImpl service = new TraceRedactionServiceImpl(new ObjectMapper());

    @Test
    void redactJsonMasksSensitiveKeysRecursively() {
        String json = """
                {
                  "authorization": "Bearer abc",
                  "nested": {
                    "apiKey": "secret-key",
                    "safe": "visible"
                  },
                  "items": [
                    {"refresh_token": "refresh"}
                  ]
                }
                """;

        String redacted = service.redactJson(json);

        assertThat(redacted).contains("\"authorization\":\"***REDACTED***\"");
        assertThat(redacted).contains("\"apiKey\":\"***REDACTED***\"");
        assertThat(redacted).contains("\"refresh_token\":\"***REDACTED***\"");
        assertThat(redacted).contains("\"safe\":\"visible\"");
        assertThat(redacted).doesNotContain("Bearer abc", "secret-key", "refresh\"}");
    }

    @Test
    void redactJsonReturnsNonJsonInputUnchanged() {
        assertThat(service.redactJson("not-json")).isEqualTo("not-json");
    }
}
