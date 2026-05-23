package com.enterprise.kb.document.model;

import com.enterprise.kb.common.constants.AssetStatus;
import com.enterprise.kb.common.constants.AssetType;
import com.enterprise.kb.common.constants.DiagramType;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 文档视觉资产实体。
 * <p>用于记录 Markdown 中图片、Mermaid/PlantUML 流程图的存储位置、语义处理状态和检索关联信息。</p>
 */
@Getter
@Setter
public class DocumentAsset {

    private UUID id;
    /** 所属文档 ID */
    private UUID documentId;
    /** 资产类型：IMAGE / DIAGRAM */
    private AssetType assetType;
    /** 流程图类型：MERMAID / PLANTUML */
    private DiagramType diagramType;
    /** 在 Markdown 源文中的出现顺序 */
    private Integer assetIndex;
    /** Markdown 中引用的原始路径 */
    private String originalPath;
    /** MinIO 中的原图或渲染图 object key */
    private String objectKey;
    /** MinIO 中的缩略图 object key */
    private String thumbnailObjectKey;
    /** 检测后的 MIME 类型 */
    private String mimeType;
    /** 资产大小（字节） */
    private Long fileSize;
    /** 所属 Markdown 章节 */
    private String section;
    /** 关联的最近文本 chunk 序号 */
    private Integer anchorChunkIndex;
    /** Markdown 图片 alt 文本 */
    private String altText;
    /** Markdown 图片 title 或推断标题 */
    private String title;
    /** Mermaid/PlantUML 源码 */
    private String sourceCode;
    /** OCR 结果 */
    private String ocrText;
    /** 模型生成的图片描述 */
    private String caption;
    /** 模型生成的检索摘要 */
    private String summary;
    /** 模型抽取的关键实体 */
    private String entities;
    /** 人工覆盖描述 */
    private String manualCaption;
    /** 人工覆盖摘要 */
    private String manualSummary;
    /** 资产处理状态 */
    private AssetStatus status = AssetStatus.PENDING;
    /** 重试次数 */
    private Integer retryCount = 0;
    /** 下一次重试时间 */
    private Instant nextRetryAt;
    /** 最近一次失败原因 */
    private String lastError;
    /** 内容 hash，用于未来增量复用 */
    private String contentHash;
    /** 扩展元数据 */
    private Map<String, Object> metadata;
    /** 创建时间 */
    private Instant createdAt = Instant.now();
    /** 最后更新时间 */
    private Instant updatedAt = Instant.now();
}
