package com.enterprise.kb.search.dto;

import java.time.Instant;
import java.util.UUID;

public record QaChatSessionDto(
        UUID id,
        UUID spaceId,
        String title,
        int messageCount,
        Instant createdAt,
        Instant updatedAt
) {}
