package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.SearchRequest;
import com.enterprise.kb.search.dto.SearchResponse;

import java.util.UUID;

/**
 * 混合搜索服务接口，结合语义搜索与关键词搜索结果进行 RRF 融合排序。
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
