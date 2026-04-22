package com.enterprise.kb.graph.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateTagRequest(
        @NotBlank @Size(max = 200) String name,
        String slug,
        String color,
        UUID parentId,
        String tagType,
        String description,
        Integer sortOrder
) {}
