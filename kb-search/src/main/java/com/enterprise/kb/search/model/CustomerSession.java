package com.enterprise.kb.search.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class CustomerSession {

    private UUID id;
    /** 会话所有者用户 ID */
    private UUID userId;
    /** 会话标题（默认取首条消息前 50 字） */
    private String title;
    /** 创建时间 */
    private Instant createdAt;
    /** 最后活跃时间 */
    private Instant updatedAt;
    /** 软删除时间，null 表示未删除 */
    private Instant deletedAt;
    /** 非列字段，由 SQL COUNT 子查询填充 */
    private int messageCount;
}
