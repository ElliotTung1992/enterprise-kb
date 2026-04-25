package com.enterprise.kb.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateMcpApiKeyRequest(
        @NotBlank @Size(max = 100) String name,
        Instant expiresAt
) {}
