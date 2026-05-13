package com.enterprise.kb.search.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 客户助手消息 DTO
 *
 * @param id        消息 ID
 * @param role      角色（user / assistant）
 * @param content   消息正文
 * @param createdAt 消息时间
 */
public record CustomerMessageDto(UUID id, String role, String content, Instant createdAt) {}
