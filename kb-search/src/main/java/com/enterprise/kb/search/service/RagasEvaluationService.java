package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.RagasConfig;
import com.enterprise.kb.search.dto.RagasItem;
import com.enterprise.kb.search.dto.RagasResult;

import java.util.List;

/**
 * Ragas 批量评估服务。
 */
public interface RagasEvaluationService {

    /**
     * 提交批量评估，阻塞直到完成或超时。
     *
     * @param items  评估输入项列表
     * @param config 本次评估配置
     * @return 评估结果列表
     */
    List<RagasResult> evaluateBatch(List<RagasItem> items, RagasConfig config);
}
