package com.enterprise.kb.search.dto;

import java.util.UUID;

public record SearchHit(
        String chunkId,
        UUID documentId,
        String documentTitle,
        String excerpt,
        Integer pageNumber,
        double score,
        String mimeType
) {}
