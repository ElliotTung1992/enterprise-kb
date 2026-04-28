package com.enterprise.kb.user.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class Space {

    private UUID id;
    /** 空间名称 */
    private String name;
    /** URL 友好 slug（唯一） */
    private String slug;
    /** 空间描述 */
    private String description;
    /** 所有者用户 ID */
    private UUID ownerId;
    /** 首选 AI 提供商（如 MINIMAX、DASHSCOPE） */
    private String preferredModelProvider;
    /** 是否启用 */
    private boolean active = true;
    /** 创建时间 */
    private Instant createdAt = Instant.now();
    /** 最后更新时间 */
    private Instant updatedAt = Instant.now();
    /** 软删除时间，为 null 表示未删除 */
    private Instant deletedAt;
}
