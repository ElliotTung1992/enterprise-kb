package com.enterprise.kb.auth.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class RefreshToken {

    private UUID id;
    /** 用户 ID */
    private UUID userId;
    /** Token 明文的 SHA-256 哈希 */
    private String tokenHash;
    /** 过期时间 */
    private Instant expiresAt;
    /** 撤销时间，为 null 表示未撤销 */
    private Instant revokedAt;
    /** 创建时间 */
    private Instant createdAt = Instant.now();

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isValid() {
        return !isExpired() && !isRevoked();
    }
}
