package com.enterprise.kb.document.model;

import com.enterprise.kb.common.constants.RelationType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class DocumentRelation {

    private UUID id;
    /** 源文档 ID */
    private UUID sourceDocId;
    /** 目标文档 ID */
    private UUID targetDocId;
    /** 关系类型（REFERENCES / RELATED_TO / EXTENDS 等） */
    private RelationType relationType;
    /** 关系权重（用于图谱排序） */
    private BigDecimal weight = BigDecimal.ONE;
    /** 是否由自动抽取生成 */
    private boolean autoDetected = false;
    /** 创建人用户 ID */
    private UUID createdBy;
    /** 创建时间 */
    private Instant createdAt = Instant.now();
}
