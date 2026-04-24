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
import java.util.concurrent.ConcurrentHashMap;

/**
 * 混合搜索服务实现，结合语义搜索与关键词搜索结果。
 * <p>使用 RRF（Reciprocal Rank Fusion）算法融合两路结果，k=60。
 * 并行执行两路搜索，再按融合分数排序返回，综合效果优于单一模式。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchServiceImpl implements HybridSearchService {

    private final SemanticSearchService semanticSearchService;
    private final KeywordSearchService keywordSearchService;
    private static final int RRF_K = 60;

    /**
     * 混合搜索。
     * <p>并行执行语义搜索与关键词搜索，使用 RRF 算法融合结果。</p>
     *
     * @param spaceId 空间 UUID
     * @param req     搜索请求
     * @return 融合后的搜索响应
     */
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

        // RRF 排序后按内容指纹二次去重，兜底存量数据中 Milvus ID 与 PostgreSQL ID 不一致的情况
        Set<String> contentSeen = new HashSet<>();
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> hitMap.get(e.getKey()))
                .filter(hit -> contentSeen.add(contentKey(hit)))
                .limit(topK)
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

    /**
     * 内容指纹：documentId + 摘要全文，用于识别 chunkId 不同但内容相同的重复条目。
     */
    private String contentKey(SearchHit hit) {
        String docId = hit.documentId() != null ? hit.documentId().toString() : "";
        String excerpt = hit.excerpt() != null ? hit.excerpt().strip() : "";
        return docId + "::" + excerpt;
    }
}
