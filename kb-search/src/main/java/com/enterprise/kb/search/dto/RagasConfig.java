package com.enterprise.kb.search.dto;

import java.util.List;

/**
 * Ragas 评估配置。
 *
 * @param judgeProvider     judge 模型提供商
 * @param judgeModel        judge 模型名称
 * @param embeddingProvider embedding 模型提供商
 * @param embeddingModel    embedding 模型名称
 * @param metrics           启用指标
 */
public record RagasConfig(
        String judgeProvider,
        String judgeModel,
        String embeddingProvider,
        String embeddingModel,
        List<String> metrics
) {}
