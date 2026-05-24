package com.enterprise.kb.user.dto;

import com.enterprise.kb.common.constants.RoleType;

import java.time.Instant;
import java.util.UUID;

/**
 * 知识空间成员 DTO。
 *
 * @param userId    用户 ID
 * @param username  用户名
 * @param email     邮箱
 * @param role      空间角色
 * @param grantedAt 授权时间
 */
public record SpaceMemberDto(
        UUID userId,
        String username,
        String email,
        RoleType role,
        Instant grantedAt
) {}
