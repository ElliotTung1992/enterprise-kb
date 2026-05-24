package com.enterprise.kb.search.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 知识库问答会话 DTO。
 *
 * @param id           会话 ID
 * @param spaceId      所属知识空间 ID
 * @param title        会话标题
 * @param messageCount 消息数量
 * @param createdAt    创建时间
 * @param updatedAt    最后活跃时间
 */
public record QaChatSessionDto(
        UUID id,
        UUID spaceId,
        String title,
        int messageCount,
        Instant createdAt,
        Instant updatedAt
) {}
