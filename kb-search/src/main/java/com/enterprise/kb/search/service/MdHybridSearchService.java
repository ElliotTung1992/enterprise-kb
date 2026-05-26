package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.SearchRequest;
import com.enterprise.kb.search.dto.SearchResponse;

import java.util.UUID;

/**
 * Markdown 混合检索服务。
 */
public interface MdHybridSearchService {

    /**
     * 融合 Markdown 语义检索和关键词检索结果。
     *
     * @param spaceId 空间 ID
     * @param req     检索请求
     * @return 融合后的结果
     */
    SearchResponse search(UUID spaceId, SearchRequest req);
}
