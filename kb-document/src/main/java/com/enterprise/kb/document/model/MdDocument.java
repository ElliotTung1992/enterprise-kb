package com.enterprise.kb.document.model;

import com.enterprise.kb.common.constants.DocumentStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class MdDocument {

    private UUID id;
    /** 所属知识空间 ID */
    private UUID spaceId;
    /** 上传人用户 ID */
    private UUID uploadedBy;
    /** Markdown 文档标题 */
    private String title;
    /** 原始文件名 */
    private String originalFilename;
    /** 原始 Markdown 文件在 MinIO 中的对象 key */
    private String objectKey;
    /** 文件大小（字节） */
    private Long fileSizeBytes;
    /** MIME 类型，通常为 text/markdown */
    private String mimeType;
    /** 入库处理状态 */
    private DocumentStatus status = DocumentStatus.PENDING;
    /** 父子切分后的 child 数量 */
    private Integer chunkCount = 0;
    /** 处理失败时的错误信息 */
    private String errorMessage;
    /** 文档扩展元数据 */
    private Map<String, Object> metadata;
    /** Markdown 标题树，供后续目录展示或调试使用 */
    private Map<String, Object> headingTree;
    /** 创建时间 */
    private Instant createdAt = Instant.now();
    /** 最后更新时间 */
    private Instant updatedAt = Instant.now();
    /** 软删除时间，为 null 表示未删除 */
    private Instant deletedAt;
}
