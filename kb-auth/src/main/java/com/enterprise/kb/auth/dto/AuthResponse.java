package com.enterprise.kb.auth.dto;

import java.util.UUID;

/**
 * 认证响应，包含访问令牌、刷新令牌和当前用户信息。
 *
 * @param accessToken  访问令牌
 * @param refreshToken 刷新令牌
 * @param tokenType    令牌类型
 * @param expiresIn    访问令牌有效期（秒）
 * @param userId       当前用户 ID
 * @param username     当前用户名
 * @param email        当前用户邮箱
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UUID userId,
        String username,
        String email
) {
    public AuthResponse(String accessToken, String refreshToken, long expiresIn,
                        UUID userId, String username, String email) {
        this(accessToken, refreshToken, "Bearer", expiresIn, userId, username, email);
    }
}
