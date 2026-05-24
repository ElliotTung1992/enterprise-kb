package com.enterprise.kb.document.dto;

import com.enterprise.kb.common.constants.AssetStatus;
import com.enterprise.kb.common.constants.AssetType;
import com.enterprise.kb.common.constants.DiagramType;

import java.time.Instant;
import java.util.UUID;

/**
 * 文档视觉资产 DTO。
 *
 * @param id               资产 ID
 * @param documentId       所属文档 ID
 * @param assetType        资产类型
 * @param diagramType      流程图类型
 * @param assetIndex       在 Markdown 源文中的出现顺序
 * @param originalPath     Markdown 中引用的原始路径
 * @param objectKey        MinIO object key
 * @param mimeType         MIME 类型
 * @param fileSize         资产大小（字节）
 * @param section          所属 Markdown 章节
 * @param anchorChunkIndex 关联的最近文本 chunk 序号
 * @param altText          Markdown 图片 alt 文本
 * @param title            资产标题
 * @param sourceCode       流程图源码
 * @param ocrText          OCR 识别文本
 * @param caption          模型生成的描述
 * @param summary          模型生成的检索摘要
 * @param entities         关键实体
 * @param manualCaption    人工修正描述
 * @param manualSummary    人工修正摘要
 * @param status           资产处理状态
 * @param lastError        最近一次错误信息
 * @param createdAt        创建时间
 * @param updatedAt        最后更新时间
 */
public record DocumentAssetDto(
        UUID id,
        UUID documentId,
        AssetType assetType,
        DiagramType diagramType,
        Integer assetIndex,
        String originalPath,
        String objectKey,
        String mimeType,
        Long fileSize,
        String section,
        Integer anchorChunkIndex,
        String altText,
        String title,
        String sourceCode,
        String ocrText,
        String caption,
        String summary,
        String entities,
        String manualCaption,
        String manualSummary,
        AssetStatus status,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
}
