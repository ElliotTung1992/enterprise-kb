package com.enterprise.kb.search.dto;

import java.util.UUID;

/**
 * 检索命中文档块 DTO。
 *
 * @param chunkId          文档分块 ID
 * @param documentId       文档 ID
 * @param documentTitle    文档标题
 * @param excerpt          命中文本摘要
 * @param pageNumber       页码
 * @param score            相关性分数
 * @param mimeType         文档 MIME 类型
 * @param contentType      分块内容形态
 * @param assetId          关联视觉资产 ID
 * @param assetUrl         关联视觉资产 URL
 * @param assetTitle       关联视觉资产标题
 * @param section          所属章节
 * @param anchorChunkIndex 关联的最近文本 chunk 序号
 */
public record SearchHit(
        String chunkId,
        UUID documentId,
        String documentTitle,
        String excerpt,
        Integer pageNumber,
        double score,
        String mimeType,
        String contentType,
        UUID assetId,
        String assetUrl,
        String assetTitle,
        String section,
        Integer anchorChunkIndex
) {}
