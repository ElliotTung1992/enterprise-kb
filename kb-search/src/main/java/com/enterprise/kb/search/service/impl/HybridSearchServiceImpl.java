package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.service.HybridSearchService;
import com.enterprise.kb.search.service.KeywordSearchService;
import com.enterprise.kb.search.service.SemanticSearchService;
import com.enterprise.kb.search.dto.SearchHit;
import com.enterprise.kb.search.dto.SearchRequest;
import com.enterprise.kb.search.dto.SearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchServiceImpl implements HybridSearchService {

    private final SemanticSearchService semanticSearchService;
    private final KeywordSearchService keywordSearchService;
    private static final int RRF_K = 60;

    @Override
    public SearchResponse search(UUID spaceId, SearchRequest req) {
        long start = System.currentTimeMillis();
        CompletableFuture<SearchResponse> semanticFuture =
                CompletableFuture.supplyAsync(() -> semanticSearchService.search(spaceId, req));
        CompletableFuture<SearchResponse> keywordFuture =
                CompletableFuture.supplyAsync(() -> keywordSearchService.search(spaceId, req));
        SearchResponse semantic = semanticFuture.join();
        SearchResponse keyword = keywordFuture.join();
        List<SearchHit> fused = reciprocalRankFusion(semantic.hits(), keyword.hits(), req.topK());
        return new SearchResponse(fused, fused.size(), "HYBRID", System.currentTimeMillis() - start);
    }

    private List<SearchHit> reciprocalRankFusion(List<SearchHit> list1, List<SearchHit> list2, int topK) {
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, SearchHit> hitMap = new LinkedHashMap<>();
        scoreList(list1, scores, hitMap);
        scoreList(list2, scores, hitMap);
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> hitMap.get(e.getKey()))
                .toList();
    }

    private void scoreList(List<SearchHit> hits, Map<String, Double> scores, Map<String, SearchHit> hitMap) {
        for (int rank = 0; rank < hits.size(); rank++) {
            SearchHit hit = hits.get(rank);
            String key = hit.chunkId() != null ? hit.chunkId()
                    : (hit.documentId() != null ? hit.documentId().toString() : "unknown") + "_" + rank;
            scores.merge(key, 1.0 / (RRF_K + rank + 1), Double::sum);
            hitMap.putIfAbsent(key, hit);
        }
    }
}
