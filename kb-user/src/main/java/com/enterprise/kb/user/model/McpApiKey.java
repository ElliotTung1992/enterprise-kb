package com.enterprise.kb.user.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class McpApiKey {

    private UUID id;
    private UUID userId;
    private String keyHash;
    private String keyPrefix;
    private String name;
    private Instant expiresAt;
    private Instant createdAt = Instant.now();
    private Instant deletedAt;

    public boolean isActive() {
        return deletedAt == null && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }
}
