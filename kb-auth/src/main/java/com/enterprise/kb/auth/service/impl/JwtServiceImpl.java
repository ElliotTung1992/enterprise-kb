package com.enterprise.kb.auth.service.impl;

import com.enterprise.kb.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 令牌服务实现，负责令牌的生成、解析与校验。
 * <p>使用 HMAC-SHA256 算法签名，支持 userId 和 username 声明的嵌入。
 * 启动时校验密钥长度不得低于 32 字节（256 bits）。</p>
 */
@Slf4j
public class JwtServiceImpl implements JwtService {

    @Value("${enterprise.kb.jwt.secret}")
    private String jwtSecret;

    @Value("${enterprise.kb.jwt.access-token-expiry-minutes:60}")
    private long accessTokenExpiryMinutes;

    @PostConstruct
    public void validateConfig() {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "enterprise.kb.jwt.secret must be at least 32 bytes (256 bits) for HMAC-SHA256");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成访问令牌。
     * <p>包含 subject（用户名）、userId 声明和 type=access 标识，
     * 有效期从配置项 enterprise.kb.jwt.access-token-expiry-minutes 读取。</p>
     *
     * @param userDetails Spring Security 用户详情
     * @param userId      用户 UUID
     * @return Base64 编码的 JWT 字符串
     */
    @Override
    public String generateAccessToken(UserDetails userDetails, UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("userId", userId.toString())
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenExpiryMinutes, ChronoUnit.MINUTES)))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析并返回令牌中所有声明。
     *
     * @param token JWT 字符串
     * @return 声明对象
     */
    @Override
    public Claims extractAllClaims(String token) {
        return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
    }

    /**
     * 从令牌中提取用户名。
     *
     * @param token JWT 字符串
     * @return 用户名
     */
    @Override
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * 从令牌中提取用户 UUID。
     *
     * @param token JWT 字符串
     * @return 用户 UUID
     */
    @Override
    public UUID extractUserId(String token) {
        return UUID.fromString(extractAllClaims(token).get("userId", String.class));
    }

    /**
     * 校验令牌是否有效且未过期。
     * <p>同时检查 type 声明必须为 access，防止 refresh token 被误用为 access token。</p>
     *
     * @param token JWT 字符串
     * @return 是否有效
     */
    @Override
    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return !claims.getExpiration().before(new Date()) && "access".equals(claims.get("type"));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Invalid JWT token: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * 获取访问令牌的过期时长（秒）。
     *
     * @return 过期秒数
     */
    @Override
    public long getAccessTokenExpirySeconds() {
        return accessTokenExpiryMinutes * 60;
    }
}
