package com.enterprise.kb.search.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

/**
 * Ragas 单条评估结果。
 *
 * @param caseId    评估用例 ID
 * @param scores    指标分数
 * @param breakdown 中间判定产物
 */
public record RagasResult(
        UUID caseId,
        Map<String, Double> scores,
        Map<String, JsonNode> breakdown
) {}
