package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.SearchRequest;
import com.enterprise.kb.search.dto.SearchResponse;

import java.util.UUID;

/**
 * Markdown 语义检索服务。
 */
public interface MdVectorSearchService {

    /**
     * 在 Markdown 专用向量集合中检索 child chunk。
     *
     * @param spaceId 空间 ID
     * @param req     检索请求
     * @return 检索响应
     */
    SearchResponse search(UUID spaceId, SearchRequest req);
}
