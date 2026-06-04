package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.tracing.TracingSupport;
import com.enterprise.kb.search.dto.SearchHit;
import com.enterprise.kb.search.dto.SearchRequest;
import com.enterprise.kb.search.dto.SearchResponse;
import com.enterprise.kb.search.service.MdHybridSearchService;
import com.enterprise.kb.search.service.MdKeywordSearchService;
import com.enterprise.kb.search.service.MdVectorSearchService;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Markdown 混合检索服务实现。
 *
 * <p>RRF 融合 Markdown 语义检索和关键词检索，返回 child 粒度命中。
 * 这里<b>不</b>按 parentId 去重——保留同一 parent 的多个命中 child，
 * 以便下游 rerank 和 parent 展开能够基于「同一 section 命中多处」做多窗节选。</p>
 */
@Service
@RequiredArgsConstructor
public class MdHybridSearchServiceImpl implements MdHybridSearchService {

    private static final int RRF_K = 60;

    private final MdVectorSearchService vectorSearchService;
    private final MdKeywordSearchService keywordSearchService;
    private final ObservationRegistry observationRegistry;
    /** 传播型检索执行器（按 bean 名 {@code retrievalExecutor} 注入，跨线程带上 tracing 上下文）。 */
    private final Executor retrievalExecutor;

    @Value("${enterprise.kb.tracing.enabled:false}")
    private boolean tracingEnabled;

    @Value("${enterprise.kb.tracing.max-retrieval-chars:4000}")
    private int maxRetrievalChars;

    /**
     * 融合 Markdown 语义检索和关键词检索结果。
     *
     * @param spaceId 空间 ID
     * @param req     检索请求
     * @return 融合后的 child 命中（未按 parent 去重）
     */
    @Override
    public SearchResponse search(UUID spaceId, SearchRequest req) {
        long start = System.currentTimeMillis();
        // 跨线程传播 ②（ADR-015）：注入传播型 retrievalExecutor，每次提交时捕获根 span 上下文与业务 holder。
        // vector 检索不再额外包 kb.retrieval.vector，交给 Spring AI VectorStore 原生 observation 产 span。
        CompletableFuture<SearchResponse> semanticFuture = CompletableFuture.supplyAsync(
                () -> vectorSearchService.search(spaceId, req),
                retrievalExecutor);
        CompletableFuture<SearchResponse> keywordFuture = CompletableFuture.supplyAsync(
                () -> TracingSupport.span(observationRegistry, tracingEnabled, "kb.retrieval.keyword")
                        .tag("kb.retrieval.top_k", String.valueOf(req.topK()))
                        .input(req.query(), maxRetrievalChars)
                        .outputFrom((SearchResponse r) -> RetrievalTraceSummary.summarize(r.hits()), maxRetrievalChars)
                        .observe(() -> keywordSearchService.search(spaceId, req)),
                retrievalExecutor);
        SearchResponse semantic = semanticFuture.join();
        SearchResponse keyword = keywordFuture.join();
        List<SearchHit> fused = reciprocalRankFusion(semantic.hits(), keyword.hits(), req.topK());
        return new SearchResponse(fused, fused.size(), "MD_HYBRID", System.currentTimeMillis() - start);
    }

    private List<SearchHit> reciprocalRankFusion(List<SearchHit> list1, List<SearchHit> list2, int topK) {
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, SearchHit> hits = new LinkedHashMap<>();
        scoreList(list1, scores, hits);
        scoreList(list2, scores, hits);
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> hits.get(entry.getKey()))
                .limit(topK)
                .toList();
    }

    private void scoreList(List<SearchHit> list, Map<String, Double> scores, Map<String, SearchHit> hits) {
        for (int rank = 0; rank < list.size(); rank++) {
            SearchHit hit = list.get(rank);
            String key = hit.chunkId() != null ? hit.chunkId() : hit.documentId() + "_" + rank;
            scores.merge(key, 1.0 / (RRF_K + rank + 1), Double::sum);
            hits.putIfAbsent(key, hit);
        }
    }
}
