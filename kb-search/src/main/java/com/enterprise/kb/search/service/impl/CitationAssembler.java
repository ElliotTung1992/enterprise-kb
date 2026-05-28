package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.ChunkContentType;
import com.enterprise.kb.search.dto.Citation;
import com.enterprise.kb.search.dto.SearchHit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 引用来源组装器，统一处理普通 RAG 和 Agentic RAG 的 citation 去重规则。
 */
final class CitationAssembler {

    private CitationAssembler() {
    }

    /**
     * 将检索命中结果转换为引用列表。
     * <p>同一视觉资产只保留一条引用，优先保留 caption/summary 类型，避免同一图片同时暴露
     * reference/source 和视觉语义 chunk。</p>
     *
     * @param hits 检索命中结果
     * @return 去重并重新编号后的引用列表
     */
    static List<Citation> fromHits(List<SearchHit> hits) {
        Map<String, SearchHit> bestHits = new LinkedHashMap<>();
        for (SearchHit hit : hits) {
            String key = citationKey(hit);
            bestHits.merge(key, hit, CitationAssembler::betterHit);
        }
        List<Citation> citations = new ArrayList<>();
        int citationNumber = 1;
        for (SearchHit hit : bestHits.values()) {
            citations.add(toCitation(citationNumber++, hit));
        }
        return citations;
    }

    /**
     * 生成引用去重 key。
     * <p>视觉资产优先按 assetId 合并；普通正文 chunk 按 chunkId 合并；兜底使用文档和摘要。</p>
     *
     * @param hit 检索命中
     * @return 引用去重 key
     */
    static String citationKey(SearchHit hit) {
        if (hit.assetId() != null) {
            return "asset:" + hit.assetId();
        }
        if (hit.chunkId() != null) {
            return "chunk:" + hit.chunkId();
        }
        String documentId = hit.documentId() != null ? hit.documentId().toString() : "";
        String excerpt = hit.excerpt() != null ? hit.excerpt().strip() : "";
        return "fallback:" + documentId + ":" + excerpt;
    }

    static SearchHit betterHit(SearchHit oldHit, SearchHit newHit) {
        int oldPriority = contentTypePriority(oldHit.contentType());
        int newPriority = contentTypePriority(newHit.contentType());
        if (newPriority != oldPriority) {
            return newPriority > oldPriority ? newHit : oldHit;
        }
        return newHit.score() > oldHit.score() ? newHit : oldHit;
    }

    private static int contentTypePriority(String contentType) {
        if (ChunkContentType.IMAGE_CAPTION.name().equals(contentType)
                || ChunkContentType.DIAGRAM_SUMMARY.name().equals(contentType)) {
            return 4;
        }
        if (ChunkContentType.TEXT.name().equals(contentType) || contentType == null) {
            return 3;
        }
        if (ChunkContentType.IMAGE_REFERENCE.name().equals(contentType)
                || ChunkContentType.DIAGRAM_SOURCE.name().equals(contentType)) {
            return 2;
        }
        return 1;
    }

    private static Citation toCitation(int citationNumber, SearchHit h) {
        return new Citation(citationNumber, h.chunkId(), h.documentId(), h.documentTitle(),
                h.excerpt(), h.pageNumber(), h.score(), h.contentType(),
                h.assetId(), h.assetUrl(), h.assetTitle(), h.section(), h.anchorChunkIndex());
    }
}
