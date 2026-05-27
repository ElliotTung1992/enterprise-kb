package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.document.mapper.MdDocumentMapper;
import com.enterprise.kb.search.dto.SearchHit;
import com.enterprise.kb.search.dto.SearchRequest;
import com.enterprise.kb.search.dto.SearchResponse;
import com.enterprise.kb.search.service.MdVectorSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Markdown 语义检索服务实现。
 */
@Slf4j
@Service
public class MdVectorSearchServiceImpl implements MdVectorSearchService {

    private final VectorStore mdVectorStore;
    private final MdDocumentMapper documentMapper;

    public MdVectorSearchServiceImpl(@Qualifier("mdVectorStore") VectorStore mdVectorStore,
                                     MdDocumentMapper documentMapper) {
        this.mdVectorStore = mdVectorStore;
        this.documentMapper = documentMapper;
    }

    /**
     * 在 Markdown 专用向量集合中检索 child chunk。
     *
     * @param spaceId 空间 ID
     * @param req     检索请求
     * @return 检索响应
     */
    @Override
    public SearchResponse search(UUID spaceId, SearchRequest req) {
        long start = System.currentTimeMillis();
        String effectiveQuery = req.semanticQuery() != null ? req.semanticQuery() : req.query();
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        var filter = b.eq("spaceId", spaceId.toString()).build();
        List<Document> results = mdVectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.builder()
                        .query(effectiveQuery)
                        .topK(req.topK())
                        .filterExpression(filter)
                        .build());
        List<SearchHit> hits = results.stream().map(this::toHit).toList();
        return new SearchResponse(hits, hits.size(), "MD_SEMANTIC", System.currentTimeMillis() - start);
    }

    private SearchHit toHit(Document document) {
        UUID documentId = UUID.fromString(document.getMetadata().get("documentId").toString());
        Object seq = document.getMetadata().get("seqInParent");
        Object assetId = document.getMetadata().get("assetId");
        return new SearchHit(document.getId(), documentId, title(documentId),
                truncate(document.getText(), 300), null,
                document.getScore() != null ? document.getScore() : 0.0,
                "text/markdown", string(document, "contentType", "MD_CHILD"),
                assetId != null ? UUID.fromString(assetId.toString()) : null,
                string(document, "assetUrl"),
                string(document, "assetTitle"),
                string(document, "section"),
                seq != null ? Integer.parseInt(seq.toString()) : null);
    }

    private String title(UUID documentId) {
        return documentMapper.findByIdAndDeletedAtIsNull(documentId).map(d -> d.getTitle()).orElse("Unknown");
    }

    private String string(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? null : value.toString();
    }

    private String string(Document document, String key, String fallback) {
        String value = string(document, key);
        return value == null ? fallback : value;
    }

    private String truncate(String text, int maxLen) {
        return text == null ? "" : (text.length() <= maxLen ? text : text.substring(0, maxLen) + "...");
    }
}
