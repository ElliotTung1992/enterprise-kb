package com.enterprise.kb.search.dto;

import java.util.List;

/**
 * 搜索响应 DTO。
 *
 * @param hits       命中结果列表
 * @param totalHits  命中数量
 * @param searchMode 搜索模式
 * @param durationMs 搜索耗时毫秒数
 */
public record SearchResponse(
        List<SearchHit> hits,
        int totalHits,
        String searchMode,
        long durationMs
) {}
