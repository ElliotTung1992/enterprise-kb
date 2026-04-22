package com.enterprise.kb.search.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.search.dto.SearchRequest;
import com.enterprise.kb.search.dto.SearchResponse;
import com.enterprise.kb.search.service.HybridSearchService;
import com.enterprise.kb.search.service.KeywordSearchService;
import com.enterprise.kb.search.service.SemanticSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 文档搜索控制器
 * <p>提供三种搜索模式：语义搜索（向量相似度）、关键词搜索（PostgreSQL 全文检索）
 * 和混合搜索（RRF 融合排序）。所有接口均通过 {@code @PreAuthorize} 进行空间级别的权限校验。</p>
 */
@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/search")
@RequiredArgsConstructor
public class SearchController {

    private final SemanticSearchService semanticSearchService;
    private final KeywordSearchService keywordSearchService;
    private final HybridSearchService hybridSearchService;

    /**
     * 语义搜索
     * <p>将查询文本向量化后在 Milvus 中进行近似最近邻检索，返回语义相关的文档片段。
     * 适用于自然语言查询，结果按向量相似度降序排列。</p>
     *
     * @param spaceId 空间 ID
     * @param req     搜索请求体，包含 query、topK 及可选的 modelProvider
     * @return 搜索响应，含命中片段列表（文档标题、相似度分数、原文摘录）及耗时
     */
    @PostMapping
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<SearchResponse>> semanticSearch(
            @PathVariable UUID spaceId,
            @Valid @RequestBody SearchRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(semanticSearchService.search(spaceId, req)));
    }

    /**
     * 关键词搜索
     * <p>使用 PostgreSQL {@code to_tsvector} / {@code to_tsquery} 对文档分块内容进行全文检索，
     * 结果按 BM25 相关度排序。适用于精确词汇匹配场景。</p>
     *
     * @param spaceId 空间 ID
     * @param req     搜索请求体，包含 query 和 topK
     * @return 搜索响应，含命中片段列表及耗时
     */
    @PostMapping("/keyword")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<SearchResponse>> keywordSearch(
            @PathVariable UUID spaceId,
            @Valid @RequestBody SearchRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(keywordSearchService.search(spaceId, req)));
    }

    /**
     * 混合搜索
     * <p>并行执行语义搜索与关键词搜索，使用 RRF（Reciprocal Rank Fusion）算法融合两路结果，
     * 兼顾语义理解和精确关键词匹配，综合效果优于单一模式。</p>
     *
     * @param spaceId 空间 ID
     * @param req     搜索请求体，包含 query、topK 及可选的 modelProvider
     * @return 融合排序后的搜索响应，含命中片段列表及耗时
     */
    @PostMapping("/hybrid")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<SearchResponse>> hybridSearch(
            @PathVariable UUID spaceId,
            @Valid @RequestBody SearchRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(hybridSearchService.search(spaceId, req)));
    }
}
