package com.enterprise.kb.search.dto;

import java.util.List;

public record SearchResponse(
        List<SearchHit> hits,
        int totalHits,
        String searchMode,
        long durationMs
) {}
