package com.enterprise.kb.graph.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 标签关联的文档摘要信息
 *
 * @param documentId       文档 ID
 * @param title            文档标题
 * @param originalFilename 原始文件名
 * @param mimeType         MIME 类型
 * @param status           文档状态
 * @param fileSizeBytes    文件大小（字节）
 * @param createdAt        创建时间
 * @param autoTagged       是否自动打标
 * @param confidence       自动打标置信度
 */
public record TagDocumentDto(
        UUID documentId,
        String title,
        String originalFilename,
        String mimeType,
        String status,
        Long fileSizeBytes,
        Instant createdAt,
        boolean autoTagged,
        BigDecimal confidence
) {}
