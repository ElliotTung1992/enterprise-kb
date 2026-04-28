package com.enterprise.kb.document.model;

import com.enterprise.kb.common.constants.DocumentStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class Document {

    private UUID id;
    /** 所属知识空间 ID */
    private UUID spaceId;
    /** 上传人用户 ID */
    private UUID uploadedBy;
    /** 文档标题（可自定义，默认为文件名） */
    private String title;
    /** 原始文件名 */
    private String originalFilename;
    /** 文件在 MinIO/本地存储的路径 */
    private String filePath;
    /** 文件大小（字节） */
    private Long fileSizeBytes;
    /** MIME 类型 */
    private String mimeType;
    /** 来源 URL（抓取场景） */
    private String sourceUrl;
    /** 处理状态（PENDING / PROCESSING / READY / FAILED） */
    private DocumentStatus status = DocumentStatus.PENDING;
    /** 处理失败时的错误信息 */
    private String errorMessage;
    /** 分块数量（处理完成后填充） */
    private Integer chunkCount = 0;
    /** 文档语言 */
    private String language;
    /** 额外元数据（JSONB） */
    private Map<String, Object> metadata;
    /** 创建时间 */
    private Instant createdAt = Instant.now();
    /** 最后更新时间 */
    private Instant updatedAt = Instant.now();
    /** 软删除时间，为 null 表示未删除 */
    private Instant deletedAt;
}
