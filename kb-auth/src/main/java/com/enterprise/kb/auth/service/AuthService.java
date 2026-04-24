package com.enterprise.kb.auth.service;

import com.enterprise.kb.auth.dto.ApiKeyExchangeResponse;
import com.enterprise.kb.auth.dto.AuthResponse;
import com.enterprise.kb.auth.dto.LoginRequest;
import com.enterprise.kb.auth.dto.RegisterRequest;

/**
 * 认证服务，提供登录、注册、令牌刷新与登出功能。
 */
public interface AuthService {

    /**
     * 用户登录。
     *
     * @param request 登录请求（用户名 + 密码）
     * @return 认证响应（含 access token、refresh token、用户信息）
     */
    AuthResponse login(LoginRequest request);

    /**
     * 用户注册。
     *
     * @param request 注册请求（用户名、密码、邮箱等）
     * @return 认证响应
     */
    AuthResponse register(RegisterRequest request);

    /**
     * 使用 refresh token 刷新 access token。
     *
     * @param rawRefreshToken 原始 refresh token 字符串
     * @return 新的认证响应
     */
    AuthResponse refresh(String rawRefreshToken);

    /**
     * 登出使 refresh token 失效。
     *
     * @param rawRefreshToken 原始 refresh token 字符串
     */
    void logout(String rawRefreshToken);

    /**
     * 修改当前用户密码。
     *
     * @param username        当前用户名
     * @param currentPassword 旧密码（明文，用于验证）
     * @param newPassword     新密码（明文）
     */
    void changePassword(String username, String currentPassword, String newPassword);

    /**
     * 用 API Key 换取短期 JWT，供 kb-mcp 调用。
     *
     * @param rawApiKey 原始 API Key 明文
     * @return 短期访问令牌响应
     */
    ApiKeyExchangeResponse exchangeApiKey(String rawApiKey);
}
