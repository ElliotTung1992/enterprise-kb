package com.enterprise.kb.user.dto;

import com.enterprise.kb.common.constants.RoleType;

import java.time.Instant;
import java.util.UUID;

public record SpaceMemberDto(
        UUID userId,
        String username,
        String email,
        RoleType role,
        Instant grantedAt
) {}
