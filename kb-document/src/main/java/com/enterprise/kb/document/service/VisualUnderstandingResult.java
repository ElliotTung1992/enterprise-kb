package com.enterprise.kb.document.service;

/**
 * 视觉理解结果。
 *
 * @param ocrText  OCR 识别文本
 * @param caption  图片或流程图描述
 * @param summary  用于检索的摘要文本
 * @param entities 关键实体
 */
public record VisualUnderstandingResult(
        String ocrText,
        String caption,
        String summary,
        String entities
) {
}
