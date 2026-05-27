package com.enterprise.kb.document.service;

/**
 * Markdown 图片理解输入。
 *
 * @param bytes     图片二进制
 * @param mimeType  图片 MIME 类型
 * @param imageUrl  Markdown 中的完整图片 URL
 * @param objectKey MinIO object key
 * @param section   所属章节
 * @param altText   Markdown 图片 alt 文本
 * @param title     Markdown 图片 title
 */
public record MdImageInput(
        byte[] bytes,
        String mimeType,
        String imageUrl,
        String objectKey,
        String section,
        String altText,
        String title
) {}

