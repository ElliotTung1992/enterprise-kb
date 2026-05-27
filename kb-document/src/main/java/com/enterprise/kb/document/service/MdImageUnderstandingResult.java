package com.enterprise.kb.document.service;

/**
 * Markdown 图片理解结果。
 *
 * @param ocrText  OCR 文字，可为空
 * @param caption  图片描述
 * @param summary  检索摘要
 * @param entities 关键实体，可为空
 */
public record MdImageUnderstandingResult(
        String ocrText,
        String caption,
        String summary,
        String entities
) {}

