package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.SearchRequest;
import com.enterprise.kb.search.dto.SearchResponse;

import java.util.UUID;

/**
 * Hybrid search service that combines semantic and keyword search using Reciprocal Rank Fusion.
 */
public interface HybridSearchService {

    /**
     * Perform a hybrid search combining semantic and keyword results.
     *
     * @param spaceId the space ID
     * @param req     the search request
     * @return the fused search response
     */
    SearchResponse search(UUID spaceId, SearchRequest req);
}
