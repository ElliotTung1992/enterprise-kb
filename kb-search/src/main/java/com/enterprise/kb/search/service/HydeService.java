package com.enterprise.kb.search.service;

/**
 * HyDE（Hypothetical Document Embeddings）服务接口。
 * <p>先让 LLM 针对用户问题生成一段假设性回答文档，再用该文档的向量去做语义检索。
 * 假设文档的向量比原始问题更接近知识库中真实答案文档的向量，从而提升召回质量。
 * 假设文档仅用于语义检索，不替换发送给 LLM 的原始问题。</p>
 */
public interface HydeService {

    /**
     * 根据用户问题生成假设性回答文档，用于替代原始问题做向量检索。
     * <p>生成失败时降级返回原始问题，保证主流程不中断。</p>
     *
     * @param question 用户原始问题
     * @return 假设性回答文档文本，失败时返回原始问题
     */
    String generateHypotheticalDocument(String question);
}
