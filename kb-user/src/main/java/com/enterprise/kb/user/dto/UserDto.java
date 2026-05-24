package com.enterprise.kb.user.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 用户信息 DTO。
 *
 * @param id        用户 ID
 * @param username  用户名
 * @param email     邮箱
 * @param fullName  用户姓名
 * @param active    是否启用
 * @param createdAt 创建时间
 */
public record UserDto(
        UUID id,
        String username,
        String email,
        String fullName,
        boolean active,
        Instant createdAt
) {}
