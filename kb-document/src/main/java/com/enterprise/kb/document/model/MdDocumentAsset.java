package com.enterprise.kb.document.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Markdown 图片资产实体。
 * <p>仅服务 md 结构化 RAG 竖井，记录 Markdown 中图片 URL、MinIO objectKey 和视觉理解结果。</p>
 */
@Getter
@Setter
public class MdDocumentAsset {

    private UUID id;
    /** 所属 Markdown 文档 ID */
    private UUID documentId;
    /** 所属知识空间 ID */
    private UUID spaceId;
    /** 对应的图片语义 child chunk ID */
    private UUID childChunkId;
    /** 图片在 Markdown 源文中的出现顺序 */
    private Integer assetIndex;
    /** Markdown 中引用的完整 MinIO 图片 URL */
    private String imageUrl;
    /** MinIO object key */
    private String objectKey;
    /** 图片 MIME 类型 */
    private String mimeType;
    /** 图片大小（字节） */
    private Long fileSize;
    /** 所属 Markdown 章节 */
    private String section;
    /** Markdown 图片 alt 文本 */
    private String altText;
    /** Markdown 图片 title 或推断标题 */
    private String title;
    /** OCR 结果 */
    private String ocrText;
    /** 模型生成的图片描述 */
    private String caption;
    /** 模型生成的检索摘要 */
    private String summary;
    /** 模型抽取的关键实体 */
    private String entities;
    /** 创建时间 */
    private Instant createdAt = Instant.now();
    /** 最后更新时间 */
    private Instant updatedAt = Instant.now();
}

