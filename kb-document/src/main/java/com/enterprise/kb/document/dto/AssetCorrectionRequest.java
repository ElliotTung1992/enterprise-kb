package com.enterprise.kb.document.dto;

/**
 * 视觉资产人工修正请求。
 *
 * @param manualCaption 人工修正后的描述
 * @param manualSummary 人工修正后的检索摘要
 */
public record AssetCorrectionRequest(
        String manualCaption,
        String manualSummary
) {
}
