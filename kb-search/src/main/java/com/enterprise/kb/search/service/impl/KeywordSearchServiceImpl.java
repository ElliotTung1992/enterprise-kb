package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.service.KeywordSearchService;
import com.enterprise.kb.search.dto.SearchHit;
import com.enterprise.kb.search.dto.SearchRequest;
import com.enterprise.kb.search.dto.SearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 关键词搜索服务实现，使用 PostgreSQL 全文检索（ts_rank）对文档块内容进行匹配。
 * <p>使用 simple 分词器，适合技术文档和代码内容。
 * 结果按 BM25 相关度排序，可用于精确术语匹配场景。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordSearchServiceImpl implements KeywordSearchService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 关键词搜索。
     *
     * @param spaceId 空间 UUID
     * @param req     搜索请求
     * @return 搜索响应
     */
    @Override
    @Transactional(readOnly = true)
    public SearchResponse search(UUID spaceId, SearchRequest req) {
        long start = System.currentTimeMillis();
        // 使用 pg_trgm similarity + ILIKE，支持中英文子串匹配；
        // 原 to_tsvector/plainto_tsquery('simple') 仅按空格切词，无法处理中文导致基本搜不到数据
        String sql = """
                SELECT dc.id::text AS chunk_id, dc.document_id::text AS document_id,
                       d.title AS document_title, dc.content AS excerpt, dc.page_number,
                       similarity(?, dc.content) AS score,
                       dc.content_type, dc.asset_id::text AS asset_id, dc.section, dc.anchor_chunk_index
                FROM document_chunks dc
                JOIN documents d ON d.id = dc.document_id
                WHERE d.space_id = ?::uuid AND d.deleted_at IS NULL
                  AND dc.content ILIKE '%' || ? || '%'
                ORDER BY score DESC LIMIT ?""";

        List<SearchHit> hits = jdbcTemplate.query(sql,
                (rs, rowNum) -> new SearchHit(
                        rs.getString("chunk_id"), UUID.fromString(rs.getString("document_id")),
                        rs.getString("document_title"), truncate(rs.getString("excerpt"), 300),
                        rs.getObject("page_number", Integer.class), rs.getDouble("score"), null,
                        rs.getString("content_type"),
                        rs.getString("asset_id") != null ? UUID.fromString(rs.getString("asset_id")) : null,
                        rs.getString("section"),
                        rs.getObject("anchor_chunk_index", Integer.class)),
                req.query(), spaceId.toString(), req.query(), req.topK());
        return new SearchResponse(hits, hits.size(), "KEYWORD", System.currentTimeMillis() - start);
    }

    private String truncate(String text, int maxLen) {
        return text == null ? "" : (text.length() <= maxLen ? text : text.substring(0, maxLen) + "…");
    }
}
