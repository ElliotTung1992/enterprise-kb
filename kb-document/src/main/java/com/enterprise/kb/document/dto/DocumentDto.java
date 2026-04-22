package com.enterprise.kb.document.dto;

import com.enterprise.kb.common.constants.DocumentStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DocumentDto(
        UUID id,
        UUID spaceId,
        UUID uploadedBy,
        String title,
        String originalFilename,
        String mimeType,
        Long fileSizeBytes,
        DocumentStatus status,
        String errorMessage,
        Integer chunkCount,
        String language,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {}
