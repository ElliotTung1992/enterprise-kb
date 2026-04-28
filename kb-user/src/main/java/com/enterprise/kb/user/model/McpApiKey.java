package com.enterprise.kb.user.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class McpApiKey {

    private UUID id;
    /** 所属用户 ID */
    private UUID userId;
    /** API Key 明文的 SHA-256 哈希 */
    private String keyHash;
    /** Key 前缀（显示给用户，明文留存） */
    private String keyPrefix;
    /** Key 名称（如"测试 Key"） */
    private String name;
    /** 过期时间，为 null 表示永不过期 */
    private Instant expiresAt;
    /** 创建时间 */
    private Instant createdAt = Instant.now();
    /** 软删除时间，为 null 表示未删除 */
    private Instant deletedAt;

    public boolean isActive() {
        return deletedAt == null && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }
}
