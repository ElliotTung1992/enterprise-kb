package com.enterprise.kb.search.dto;

import java.time.Instant;
import java.util.UUID;

public record QaChatMessageDto(
        UUID id,
        String role,
        String content,
        Instant createdAt
) {}
