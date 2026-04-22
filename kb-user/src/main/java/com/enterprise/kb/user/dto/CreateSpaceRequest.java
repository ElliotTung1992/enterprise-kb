package com.enterprise.kb.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSpaceRequest(
        @NotBlank @Size(max = 255) String name,
        String slug,
        String description,
        String preferredModelProvider
) {}
