package com.enterprise.kb.search.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ragas 评估配置属性。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "enterprise.kb.eval.ragas")
public class RagasEvalProperties {

    /** 是否启用 Ragas 评估 */
    private boolean enabled = true;
    /** Python evaluation 服务地址 */
    private String endpoint = "http://localhost:8000";
    /** 默认 judge 模型提供商 */
    private String judgeProvider = "DASHSCOPE";
    /** 默认 judge 模型 */
    private String judgeModel = "qwen-max";
    /** 默认 embedding 模型提供商 */
    private String embeddingProvider = "DASHSCOPE";
    /** 默认 embedding 模型 */
    private String embeddingModel = "text-embedding-v3";
    /** 轮询间隔秒数 */
    private int pollIntervalSeconds = 5;
    /** 超时分钟数 */
    private int timeoutMinutes = 30;
    /** 每批最大条数 */
    private int maxItemsPerBatch = 100;
    /** 门禁模式：warn / soft / hard */
    private String gateMode = "warn";
    /** 默认指标 */
    private List<String> metrics = List.of("faithfulness", "answer_relevancy", "context_precision", "context_recall");
    /** 默认阈值 */
    private Map<String, Double> thresholds = new LinkedHashMap<>(Map.of(
            "faithfulness", 0.85d,
            "answer_relevancy", 0.80d,
            "context_precision", 0.70d,
            "context_recall", 0.75d
    ));
}
