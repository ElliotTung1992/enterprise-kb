package com.enterprise.kb.document.dto;

import com.enterprise.kb.common.constants.RelationType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddRelationRequest(
        @NotNull UUID targetDocId,
        @NotNull RelationType relationType
) {}
