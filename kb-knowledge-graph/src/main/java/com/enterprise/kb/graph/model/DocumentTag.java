package com.enterprise.kb.graph.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class DocumentTag {

    private UUID id;
    /** 文档 ID */
    private UUID documentId;
    /** 标签 ID */
    private UUID tagId;
    /** 打标人用户 ID */
    private UUID taggedBy;
    /** 是否由自动打标生成 */
    private boolean autoTagged = false;
    /** 自动打标的置信度（0.0000 ~ 1.0000） */
    private BigDecimal confidence;
    /** 打标时间 */
    private Instant createdAt = Instant.now();
}
