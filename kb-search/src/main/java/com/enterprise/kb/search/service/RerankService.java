package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.SearchHit;

import java.util.List;

/**
 * Rerank 精排服务接口，对混合检索的候选结果按与问题的相关性重新排序。
 * <p>Post-retrieval 阶段：混合检索宽召回（topK×4），Rerank 精排缩减至最终 topK，
 * 交叉编码器比向量点积有更细粒度的语义理解，显著提升最终结果精度。</p>
 */
public interface RerankService {

    /**
     * 对候选文档列表按与问题的相关性重新排序，返回 topN 条。
     * <p>调用失败时降级返回原始列表的前 topN 条，保证主流程不中断。</p>
     *
     * @param question 用户原始问题
     * @param hits     混合检索候选列表
     * @param topN     精排后保留条数
     * @return 重新排序后的 topN 条结果，score 替换为 Rerank 相关性分数
     */
    List<SearchHit> rerank(String question, List<SearchHit> hits, int topN);
}
