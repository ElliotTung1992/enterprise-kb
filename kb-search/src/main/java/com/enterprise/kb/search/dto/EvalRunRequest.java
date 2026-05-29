package com.enterprise.kb.search.dto;

import java.util.List;
import java.util.Map;

/**
 * 启动评估运行请求。
 *
 * @param type              评估类型，RAGAS 表示运行 Ragas 语义评测
 * @param dataset           数据集名称
 * @param targetService     目标服务：MdQnAService / MdAgenticQnAService
 * @param judgeProvider     judge 模型提供商
 * @param judgeModel        judge 模型名称
 * @param embeddingProvider embedding 模型提供商
 * @param embeddingModel    embedding 模型名称
 * @param metrics           启用指标
 * @param thresholds        单次运行阈值覆盖
 */
public record EvalRunRequest(
        String type,
        String dataset,
        String targetService,
        String judgeProvider,
        String judgeModel,
        String embeddingProvider,
        String embeddingModel,
        List<String> metrics,
        Map<String, Double> thresholds
) {}
