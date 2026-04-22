package com.enterprise.kb.user.dto;

import java.time.Instant;
import java.util.UUID;

public record SpaceDto(
        UUID id,
        String name,
        String slug,
        String description,
        UUID ownerId,
        String preferredModelProvider,
        boolean active,
        Instant createdAt
) {}
