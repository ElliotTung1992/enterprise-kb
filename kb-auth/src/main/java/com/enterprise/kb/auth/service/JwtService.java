package com.enterprise.kb.auth.service;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

/**
 * JWT 令牌服务，提供令牌的生成、解析与校验。
 */
public interface JwtService {

    /**
     * 生成访问令牌。
     *
     * @param userDetails Spring Security 用户详情
     * @param userId      用户 UUID
     * @return Base64 编码的 JWT 字符串
     */
    String generateAccessToken(UserDetails userDetails, UUID userId);

    /**
     * 解析并返回令牌中所有声明。
     *
     * @param token JWT 字符串
     * @return 声明对象
     */
    Claims extractAllClaims(String token);

    /**
     * 从令牌中提取用户名。
     *
     * @param token JWT 字符串
     * @return 用户名
     */
    String extractUsername(String token);

    /**
     * 从令牌中提取用户 UUID。
     *
     * @param token JWT 字符串
     * @return 用户 UUID
     */
    UUID extractUserId(String token);

    /**
     * 校验令牌是否有效且未过期。
     *
     * @param token JWT 字符串
     * @return 是否有效
     */
    boolean isTokenValid(String token);

    /**
     * 获取访问令牌的过期时长（秒）。
     *
     * @return 过期秒数
     */
    long getAccessTokenExpirySeconds();
}
