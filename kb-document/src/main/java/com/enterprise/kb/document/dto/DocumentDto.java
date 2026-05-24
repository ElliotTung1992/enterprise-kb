package com.enterprise.kb.document.dto;

import com.enterprise.kb.common.constants.DocumentStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 文档元数据 DTO。
 *
 * @param id               文档 ID
 * @param spaceId          所属知识空间 ID
 * @param uploadedBy       上传用户 ID
 * @param title            文档标题
 * @param originalFilename 原始文件名
 * @param mimeType         MIME 类型
 * @param fileSizeBytes    文件大小（字节）
 * @param status           文档处理状态
 * @param errorMessage     处理失败信息
 * @param chunkCount       分块数量
 * @param language         文档语言
 * @param metadata         扩展元数据
 * @param createdAt        创建时间
 * @param updatedAt        最后更新时间
 */
public record DocumentDto(
        UUID id,
        UUID spaceId,
        UUID uploadedBy,
        String title,
        String originalFilename,
        String mimeType,
        Long fileSizeBytes,
        DocumentStatus status,
        String errorMessage,
        Integer chunkCount,
        String language,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {}
