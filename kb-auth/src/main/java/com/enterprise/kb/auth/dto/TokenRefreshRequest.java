package com.enterprise.kb.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新访问令牌请求。
 *
 * @param refreshToken 刷新令牌
 */
public record TokenRefreshRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {}
