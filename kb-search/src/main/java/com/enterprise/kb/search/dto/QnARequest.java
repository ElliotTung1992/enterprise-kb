package com.enterprise.kb.search.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record QnARequest(
        @NotBlank String question,
        UUID sessionId,
        String modelProvider,
        String modelName,
        int topK
) {
    public QnARequest {
        topK = topK <= 0 ? 5 : topK;
    }
}
