package com.enterprise.kb.search.dto;

import java.util.UUID;

public record Citation(
        String chunkId,
        UUID documentId,
        String documentTitle,
        String excerpt,
        Integer pageNumber,
        double score
) {}
