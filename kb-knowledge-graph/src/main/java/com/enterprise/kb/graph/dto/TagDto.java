package com.enterprise.kb.graph.dto;

import java.time.Instant;
import java.util.UUID;

public record TagDto(
        UUID id,
        UUID spaceId,
        String name,
        String slug,
        String color,
        UUID parentId,
        String tagType,
        String description,
        Integer sortOrder,
        Instant createdAt
) {}
