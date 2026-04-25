package com.enterprise.kb.graph.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 标签关联的文档摘要信息
 */
public record TagDocumentDto(
        UUID documentId,
        String title,
        String originalFilename,
        String mimeType,
        String status,
        Long fileSizeBytes,
        Instant createdAt,
        boolean autoTagged,
        BigDecimal confidence
) {}
