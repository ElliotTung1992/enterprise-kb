package com.enterprise.kb.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        String username,
        String email,
        String fullName,
        boolean active,
        Instant createdAt
) {}
