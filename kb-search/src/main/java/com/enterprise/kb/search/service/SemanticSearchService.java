package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.SearchRequest;
import com.enterprise.kb.search.dto.SearchResponse;

import java.util.UUID;

/**
 * Semantic search service using a vector store and embedding models.
 */
public interface SemanticSearchService {

    /**
     * Perform a semantic similarity search against the vector store.
     *
     * @param spaceId the space ID
     * @param req     the search request
     * @return the search response with similarity-scored results
     */
    SearchResponse search(UUID spaceId, SearchRequest req);
}
