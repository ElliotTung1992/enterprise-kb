package com.enterprise.kb.search.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 知识库问答会话消息 DTO。
 *
 * @param id        消息 ID
 * @param role      消息角色
 * @param content   消息内容
 * @param createdAt 创建时间
 */
public record QaChatMessageDto(
        UUID id,
        String role,
        String content,
        Instant createdAt
) {}
