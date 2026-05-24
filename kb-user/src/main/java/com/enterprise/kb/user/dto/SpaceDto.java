package com.enterprise.kb.user.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 知识空间 DTO。
 *
 * @param id                     空间 ID
 * @param name                   空间名称
 * @param slug                   空间标识
 * @param description            空间描述
 * @param ownerId                所有者用户 ID
 * @param preferredModelProvider 首选模型提供商
 * @param active                 是否启用
 * @param createdAt              创建时间
 */
public record SpaceDto(
        UUID id,
        String name,
        String slug,
        String description,
        UUID ownerId,
        String preferredModelProvider,
        boolean active,
        Instant createdAt
) {}
