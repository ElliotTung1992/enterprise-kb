package com.enterprise.kb.user.dto;

import com.enterprise.kb.common.constants.RoleType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SpaceMemberRequest(
        @NotNull UUID userId,
        @NotNull RoleType role
) {}
