package com.enterprise.kb.search.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 搜索请求 DTO。
 *
 * @param query         查询文本
 * @param topK          返回结果数量
 * @param modelProvider 模型提供商
 * @param filters       搜索过滤条件
 * @param semanticQuery 语义检索查询文本
 */
public record SearchRequest(
        @NotBlank String query,
        @Positive int topK,
        String modelProvider,
        SearchFilters filters,
        // HyDE 生成的假设文档，仅用于语义向量检索；null 时与 query 相同
        String semanticQuery
) {
    public SearchRequest {
        topK = topK <= 0 ? 10 : topK;
    }

    /** 向后兼容构造器，不传 semanticQuery 时默认 null（语义检索复用 query）。 */
    public SearchRequest(String query, int topK, String modelProvider, SearchFilters filters) {
        this(query, topK, modelProvider, filters, null);
    }

    /**
     * 搜索过滤条件。
     *
     * @param tagIds    标签 ID 列表
     * @param dateFrom  创建时间起点
     * @param dateTo    创建时间终点
     * @param mimeTypes MIME 类型列表
     */
    public record SearchFilters(
            List<UUID> tagIds,
            Instant dateFrom,
            Instant dateTo,
            List<String> mimeTypes
    ) {}
}
