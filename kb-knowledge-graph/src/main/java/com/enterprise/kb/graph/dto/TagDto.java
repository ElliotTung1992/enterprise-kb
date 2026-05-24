package com.enterprise.kb.graph.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 标签 DTO。
 *
 * @param id          标签 ID
 * @param spaceId     所属知识空间 ID
 * @param name        标签名称
 * @param slug        标签标识
 * @param color       标签颜色
 * @param parentId    父标签 ID
 * @param tagType     标签类型
 * @param description 标签描述
 * @param sortOrder   排序值
 * @param createdAt   创建时间
 */
public record TagDto(
        UUID id,
        UUID spaceId,
        String name,
        String slug,
        String color,
        UUID parentId,
        String tagType,
        String description,
        Integer sortOrder,
        Instant createdAt
) {}
