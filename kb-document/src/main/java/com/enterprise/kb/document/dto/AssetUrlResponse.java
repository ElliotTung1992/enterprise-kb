package com.enterprise.kb.document.dto;

/**
 * 视觉资产短期访问 URL 响应。
 *
 * @param url              短期访问 URL
 * @param expiresInSeconds URL 有效期（秒）
 */
public record AssetUrlResponse(
        String url,
        int expiresInSeconds
) {
}
