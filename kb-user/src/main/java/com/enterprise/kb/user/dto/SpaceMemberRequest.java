package com.enterprise.kb.user.dto;

import com.enterprise.kb.common.constants.RoleType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 知识空间成员变更请求。
 *
 * @param userId 用户 ID
 * @param role   空间角色
 */
public record SpaceMemberRequest(
        @NotNull UUID userId,
        @NotNull RoleType role
) {}
