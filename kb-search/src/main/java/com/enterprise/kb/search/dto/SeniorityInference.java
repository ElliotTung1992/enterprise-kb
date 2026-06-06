package com.enterprise.kb.search.dto;

import com.enterprise.kb.common.constants.Seniority;

/**
 * 资历推断结果（ADR-016，Phase 2）。
 *
 * @param seniority  推断出的资历
 * @param confidence 置信度（0~1），低于阈值不应用
 * @param reason     一句判定理由（便于排查，不入画像）
 */
public record SeniorityInference(Seniority seniority, double confidence, String reason) {
}
