package com.enterprise.kb.search.service;

/**
 * 查询改写服务接口，用于 Pre-retrieval 阶段优化检索质量。
 * <p>将用户口语化、模糊的问题改写为适合知识库检索的标准查询语句，
 * 改写结果仅用于检索，不替换发送给 LLM 的原始问题。</p>
 */
public interface QueryRewriteService {

    /**
     * 将原始问题改写为更适合知识库检索的查询语句。
     * <p>改写失败时降级返回原始问题，保证主流程不中断。</p>
     *
     * @param question 用户原始问题
     * @return 改写后的检索查询，失败时返回原始问题
     */
    String rewrite(String question);
}
