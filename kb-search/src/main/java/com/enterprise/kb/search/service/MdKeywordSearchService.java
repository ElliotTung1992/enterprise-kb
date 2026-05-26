package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.SearchRequest;
import com.enterprise.kb.search.dto.SearchResponse;

import java.util.UUID;

/**
 * Markdown 关键词检索服务。
 */
public interface MdKeywordSearchService {

    /**
     * 在 Markdown child 检索文本上执行关键词检索。
     *
     * @param spaceId 空间 ID
     * @param req     检索请求
     * @return 检索响应
     */
    SearchResponse search(UUID spaceId, SearchRequest req);
}
