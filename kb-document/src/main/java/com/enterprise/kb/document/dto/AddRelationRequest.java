package com.enterprise.kb.document.dto;

import com.enterprise.kb.common.constants.RelationType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 手动添加文档关系请求。
 *
 * @param targetDocId  目标文档 ID
 * @param relationType 关系类型
 */
public record AddRelationRequest(
        @NotNull UUID targetDocId,
        @NotNull RelationType relationType
) {}
