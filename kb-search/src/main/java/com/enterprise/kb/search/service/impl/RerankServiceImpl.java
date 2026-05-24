package com.enterprise.kb.search.service.impl;

import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
import com.alibaba.cloud.ai.model.RerankModel;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import com.enterprise.kb.search.dto.SearchHit;
import com.enterprise.kb.search.service.RerankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rerank 精排服务实现，使用 DashScope gte-rerank 模型对检索候选进行相关性重排。
 * <p>用列表下标作为 Document ID，精排后按下标反查原始 SearchHit，
 * 并将 Rerank 相关性分数替换原 RRF 分数。调用失败时降级返回原始前 topN 条。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RerankServiceImpl implements RerankService {

    private final RerankModel rerankModel;

    /** 传给 Rerank 模型的单条文档最大字符数，避免超出模型 token 限制 */
    private static final int MAX_EXCERPT_CHARS = 500;

    /**
     * 对候选文档列表按与问题的相关性重新排序，返回 topN 条。
     *
     * @param question 用户原始问题
     * @param hits     混合检索候选列表
     * @param topN     精排后保留条数
     * @return 重新排序后的 topN 条结果
     */
    @Override
    public List<SearchHit> rerank(String question, List<SearchHit> hits, int topN) {
        if (hits.isEmpty()) return hits;
        int effectiveTopN = Math.min(topN, hits.size());

        try {
            // 用列表下标作为 Document ID，精排完成后通过 ID 反查原始 SearchHit
            List<Document> documents = new ArrayList<>();
            for (int i = 0; i < hits.size(); i++) {
                documents.add(new Document(
                        String.valueOf(i),
                        truncate(hits.get(i).excerpt(), MAX_EXCERPT_CHARS),
                        Map.of()
                ));
            }

            DashScopeRerankOptions options = DashScopeRerankOptions.builder()
                    .withTopN(effectiveTopN)
                    .withReturnDocuments(false)
                    .build();

            RerankResponse response = rerankModel.call(new RerankRequest(question, documents, options));

            List<SearchHit> reranked = response.getResults().stream()
                    .map(result -> {
                        int originalIndex = Integer.parseInt(result.getOutput().getId());
                        SearchHit original = hits.get(originalIndex);
                        // 用 Rerank 相关性分数替换原来的 RRF 分数
                        return new SearchHit(original.chunkId(), original.documentId(),
                                original.documentTitle(), original.excerpt(),
                                original.pageNumber(), result.getScore(), original.mimeType(),
                                original.contentType(), original.assetId(), original.section(), original.anchorChunkIndex());
                    })
                    .toList();

            log.debug("Rerank 完成：候选数={}，精排保留={}，问题=\"{}\"",
                    hits.size(), reranked.size(), question);
            return reranked;

        } catch (Exception e) {
            log.warn("Rerank 调用失败，降级返回 RRF 前 {} 条，原因: {}", effectiveTopN, e.getMessage());
            return hits.subList(0, effectiveTopN);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }
}
