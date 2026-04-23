package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.SearchRequest;
import com.enterprise.kb.search.dto.SearchResponse;

import java.util.UUID;

/**
 * 关键词搜索服务接口，使用 PostgreSQL 全文检索对文档块内容进行匹配。
 */
public interface KeywordSearchService {

    /**
     * Perform a keyword search using PostgreSQL ts_rank.
     *
     * @param spaceId the space ID
     * @param req     the search request
     * @return the search response with ranked results
     */
    SearchResponse search(UUID spaceId, SearchRequest req);
}
