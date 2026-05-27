package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.dto.SearchHit;
import com.enterprise.kb.search.dto.SearchRequest;
import com.enterprise.kb.search.dto.SearchResponse;
import com.enterprise.kb.search.service.MdKeywordSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Markdown 关键词检索服务实现。
 */
@Service
@RequiredArgsConstructor
public class MdKeywordSearchServiceImpl implements MdKeywordSearchService {

    /** 合法 SQL 标识符：tokenizer / index 名取自服务端配置（非用户输入），仍做白名单校验后内联。 */
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    /** 两条 SQL 分支返回同形 SearchHit，统一映射。 */
    private static final RowMapper<SearchHit> HIT_MAPPER = (rs, rowNum) -> new SearchHit(
            rs.getString("chunk_id"),
            UUID.fromString(rs.getString("document_id")),
            rs.getString("document_title"),
            truncate(rs.getString("excerpt"), 300),
            null,
            rs.getDouble("score"),
            "text/markdown",
            rs.getString("content_type"),
            rs.getString("asset_id") == null ? null : UUID.fromString(rs.getString("asset_id")),
            rs.getString("asset_url"),
            rs.getString("asset_title"),
            rs.getString("section"),
            rs.getObject("seq_in_parent", Integer.class));

    private final JdbcTemplate jdbcTemplate;

    /** md 关键词路算法：TRGM(默认，pg_trgm 子串相似度) / BM25(vchord_bm25 词级打分)。 */
    @Value("${enterprise.kb.md.keyword-mode:TRGM}")
    private String keywordMode;

    /** 索引与查询共用的 pg_tokenizer 配置名（建于运维脚本 db/manual/md-bm25-build.sql）。 */
    @Value("${enterprise.kb.md.bm25-tokenizer:md_tok}")
    private String bm25Tokenizer;

    /** vchord_bm25 索引名，to_bm25query 按名引用。 */
    @Value("${enterprise.kb.md.bm25-index:idx_md_child_bm25}")
    private String bm25Index;

    /**
     * 在 Markdown child 检索文本上执行关键词检索。
     *
     * <p>按 {@code enterprise.kb.md.keyword-mode} 在 TRGM 与 BM25 两条 SQL 间切换：
     * 二者返回同形 {@link SearchHit} 列表（{@code MD_CHILD} 粒度、字段一致），上游
     * {@code MdHybridSearchServiceImpl} 按排名 RRF 融合，对分数符号与量纲无感，故无需归一化。</p>
     *
     * @param spaceId 空间 ID
     * @param req     检索请求
     * @return 检索响应
     */
    @Override
    @Transactional(readOnly = true)
    public SearchResponse search(UUID spaceId, SearchRequest req) {
        long start = System.currentTimeMillis();
        List<SearchHit> hits = "BM25".equalsIgnoreCase(keywordMode)
                ? bm25Search(spaceId, req)
                : trgmSearch(spaceId, req);
        return new SearchResponse(hits, hits.size(), "MD_KEYWORD", System.currentTimeMillis() - start);
    }

    /**
     * 现状实现：pg_trgm 三元组相似度 + ILIKE 子串匹配，分数越大越相关。
     */
    private List<SearchHit> trgmSearch(UUID spaceId, SearchRequest req) {
        String sql = """
                SELECT mc.id::text AS chunk_id, mc.document_id::text AS document_id,
                       md.title AS document_title, mc.embed_text AS excerpt,
                       similarity(?, mc.embed_text) AS score,
                       mc.content_type, mc.asset_id::text AS asset_id,
                       ma.image_url AS asset_url, COALESCE(ma.title, ma.alt_text) AS asset_title,
                       mc.section, mc.seq_in_parent
                FROM md_child_chunk mc
                JOIN md_documents md ON md.id = mc.document_id
                LEFT JOIN md_document_assets ma ON ma.id = mc.asset_id
                WHERE mc.space_id = ?::uuid AND md.deleted_at IS NULL
                  AND mc.embed_text ILIKE '%' || ? || '%'
                ORDER BY score DESC LIMIT ?""";
        return jdbcTemplate.query(sql, HIT_MAPPER,
                req.query(), spaceId.toString(), req.query(), req.topK());
    }

    /**
     * BM25 实现：query 经同一 tokenizer 切词，按 vchord_bm25 打分排序。
     *
     * <p>BM25 分为<b>负值</b>，越负越相关，故 {@code ORDER BY score ASC}。tokenizer / index 名为
     * 服务端配置常量（非用户输入），白名单校验后内联；用户 query 走 {@code ?} 绑定防注入。
     * 过滤 {@code bm25vector IS NOT NULL} 以排除冷启动 / 回填前的脏行。</p>
     */
    private List<SearchHit> bm25Search(UUID spaceId, SearchRequest req) {
        String index = requireIdentifier(bm25Index);
        String tokenizer = requireIdentifier(bm25Tokenizer);
        String sql = String.format("""
                SELECT mc.id::text AS chunk_id, mc.document_id::text AS document_id,
                       md.title AS document_title, mc.embed_text AS excerpt,
                       (mc.bm25vector <&> to_bm25query('%s', tokenize(?, '%s'))) AS score,
                       mc.content_type, mc.asset_id::text AS asset_id,
                       ma.image_url AS asset_url, COALESCE(ma.title, ma.alt_text) AS asset_title,
                       mc.section, mc.seq_in_parent
                FROM md_child_chunk mc
                JOIN md_documents md ON md.id = mc.document_id
                LEFT JOIN md_document_assets ma ON ma.id = mc.asset_id
                WHERE mc.space_id = ?::uuid AND md.deleted_at IS NULL
                  AND mc.bm25vector IS NOT NULL
                ORDER BY score ASC LIMIT ?""", index, tokenizer);
        return jdbcTemplate.query(sql, HIT_MAPPER,
                req.query(), spaceId.toString(), req.topK());
    }

    private String requireIdentifier(String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalStateException("非法 BM25 标识符配置: " + value);
        }
        return value;
    }

    private static String truncate(String text, int maxLen) {
        return text == null ? "" : (text.length() <= maxLen ? text : text.substring(0, maxLen) + "...");
    }
}
