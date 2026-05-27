package com.enterprise.kb.document.service;

/**
 * Markdown 图片 URL 解析结果。
 *
 * @param imageUrl  Markdown 中的完整图片 URL
 * @param objectKey MinIO object key
 */
public record MdImageReference(String imageUrl, String objectKey) {}

