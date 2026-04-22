package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.service.SemanticSearchService;
import com.enterprise.kb.document.mapper.DocumentMapper;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.dto.SearchHit;
import com.enterprise.kb.search.dto.SearchRequest;
import com.enterprise.kb.search.dto.SearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchServiceImpl implements SemanticSearchService {

    private final VectorStore vectorStore;
    private final ModelProviderResolver modelProviderResolver;
    private final DocumentMapper documentMapper;

    @Override
    public SearchResponse search(UUID spaceId, SearchRequest req) {
        long start = System.currentTimeMillis();
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        var filter = b.eq("spaceId", spaceId.toString()).build();
        List<Document> results = vectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.builder()
                        .query(req.query()).topK(req.topK()).filterExpression(filter).build());
        List<SearchHit> hits = results.stream().map(doc -> {
            String docIdStr = (String) doc.getMetadata().get("documentId");
            UUID documentId = docIdStr != null ? UUID.fromString(docIdStr) : null;
            Object pageNum = doc.getMetadata().get("page_number");
            Integer page = pageNum != null ? Integer.parseInt(pageNum.toString()) : null;
            double score = doc.getScore() != null ? doc.getScore() : 0.0;
            return new SearchHit(doc.getId(), documentId, resolveDocTitle(documentId),
                    truncate(doc.getText(), 300), page, score, null);
        }).toList();
        return new SearchResponse(hits, hits.size(), "SEMANTIC", System.currentTimeMillis() - start);
    }

    private String resolveDocTitle(UUID documentId) {
        if (documentId == null) return "Unknown";
        return documentMapper.findByIdAndDeletedAtIsNull(documentId)
                .map(d -> d.getTitle()).orElse("Unknown");
    }

    private String truncate(String text, int maxLen) {
        return text == null ? "" : (text.length() <= maxLen ? text : text.substring(0, maxLen) + "…");
    }
}
