package com.enterprise.kb.search.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 客户助手会话列表 DTO
 *
 * @param id           会话 ID
 * @param title        会话标题
 * @param messageCount 消息数量
 * @param createdAt    创建时间
 * @param updatedAt    最后活跃时间
 */
public record CustomerSessionDto(UUID id, String title, int messageCount,
                                 Instant createdAt, Instant updatedAt) {}
