package com.enterprise.kb.user.dto;

import java.time.Instant;
import java.util.UUID;

public record McpApiKeyDto(
        UUID id,
        String keyPrefix,
        String name,
        Instant expiresAt,
        Instant createdAt
) {}
