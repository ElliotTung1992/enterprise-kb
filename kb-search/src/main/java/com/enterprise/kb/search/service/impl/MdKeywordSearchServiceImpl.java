package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.dto.SearchHit;
import com.enterprise.kb.search.dto.SearchRequest;
import com.enterprise.kb.search.dto.SearchResponse;
import com.enterprise.kb.search.service.MdKeywordSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Markdown 关键词检索服务实现。
 */
@Service
@RequiredArgsConstructor
public class MdKeywordSearchServiceImpl implements MdKeywordSearchService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 在 Markdown child 检索文本上执行关键词检索。
     *
     * @param spaceId 空间 ID
     * @param req     检索请求
     * @return 检索响应
     */
    @Override
    @Transactional(readOnly = true)
    public SearchResponse search(UUID spaceId, SearchRequest req) {
        long start = System.currentTimeMillis();
        String sql = """
                SELECT mc.id::text AS chunk_id, mc.document_id::text AS document_id,
                       md.title AS document_title, mc.embed_text AS excerpt,
                       similarity(?, mc.embed_text) AS score,
                       mc.section, mc.seq_in_parent
                FROM md_child_chunk mc
                JOIN md_documents md ON md.id = mc.document_id
                WHERE mc.space_id = ?::uuid AND md.deleted_at IS NULL
                  AND mc.embed_text ILIKE '%' || ? || '%'
                ORDER BY score DESC LIMIT ?""";
        List<SearchHit> hits = jdbcTemplate.query(sql,
                (rs, rowNum) -> new SearchHit(
                        rs.getString("chunk_id"),
                        UUID.fromString(rs.getString("document_id")),
                        rs.getString("document_title"),
                        truncate(rs.getString("excerpt"), 300),
                        null,
                        rs.getDouble("score"),
                        "text/markdown",
                        "MD_CHILD",
                        null,
                        rs.getString("section"),
                        rs.getObject("seq_in_parent", Integer.class)),
                req.query(), spaceId.toString(), req.query(), req.topK());
        return new SearchResponse(hits, hits.size(), "MD_KEYWORD", System.currentTimeMillis() - start);
    }

    private String truncate(String text, int maxLen) {
        return text == null ? "" : (text.length() <= maxLen ? text : text.substring(0, maxLen) + "...");
    }
}
