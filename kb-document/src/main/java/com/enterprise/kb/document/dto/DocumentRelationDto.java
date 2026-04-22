package com.enterprise.kb.document.dto;

import com.enterprise.kb.common.constants.RelationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DocumentRelationDto(
        UUID id,
        UUID sourceDocId,
        UUID targetDocId,
        RelationType relationType,
        BigDecimal weight,
        boolean autoDetected,
        Instant createdAt
) {}
