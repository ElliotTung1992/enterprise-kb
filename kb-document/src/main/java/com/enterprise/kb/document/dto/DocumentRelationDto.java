package com.enterprise.kb.document.dto;

import com.enterprise.kb.common.constants.RelationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 文档关系 DTO。
 *
 * @param id           关系 ID
 * @param sourceDocId  源文档 ID
 * @param targetDocId  目标文档 ID
 * @param relationType 关系类型
 * @param weight       关系权重
 * @param autoDetected 是否自动识别
 * @param createdAt    创建时间
 */
public record DocumentRelationDto(
        UUID id,
        UUID sourceDocId,
        UUID targetDocId,
        RelationType relationType,
        BigDecimal weight,
        boolean autoDetected,
        Instant createdAt
) {}
