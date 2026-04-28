package com.enterprise.kb.search.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class QaChatSession {

    private UUID id;
    private UUID spaceId;
    private UUID userId;
    private String title;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    /** 非列字段，由 SQL COUNT 子查询填充 */
    private int messageCount;
}
