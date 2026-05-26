package com.enterprise.kb.document.dto;

import com.enterprise.kb.common.constants.DocumentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Markdown 结构化文档 DTO。
 *
 * @param id               文档 ID
 * @param spaceId          空间 ID
 * @param title            文档标题
 * @param originalFilename 原始文件名
 * @param fileSizeBytes    文件大小
 * @param mimeType         MIME 类型
 * @param status           处理状态
 * @param chunkCount       child chunk 数量
 * @param errorMessage     错误信息
 * @param createdAt        创建时间
 * @param updatedAt        更新时间
 */
public record MdDocumentDto(
        UUID id,
        UUID spaceId,
        String title,
        String originalFilename,
        Long fileSizeBytes,
        String mimeType,
        DocumentStatus status,
        Integer chunkCount,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {}
