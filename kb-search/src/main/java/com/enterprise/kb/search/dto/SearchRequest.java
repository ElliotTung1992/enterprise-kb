package com.enterprise.kb.search.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SearchRequest(
        @NotBlank String query,
        @Positive int topK,
        String modelProvider,
        SearchFilters filters
) {
    public SearchRequest {
        topK = topK <= 0 ? 10 : topK;
    }

    public record SearchFilters(
            List<UUID> tagIds,
            Instant dateFrom,
            Instant dateTo,
            List<String> mimeTypes
    ) {}
}
