package com.enterprise.kb.search.dto;

import java.util.UUID;

/**
 * 问答引用来源 DTO。
 *
 * @param citationNumber   引用编号
 * @param chunkId          文档分块 ID
 * @param documentId       文档 ID
 * @param documentTitle    文档标题
 * @param excerpt          引用片段
 * @param pageNumber       页码
 * @param score            检索相关性分数
 * @param contentType      分块内容类型
 * @param assetId          关联视觉资产 ID
 * @param assetUrl         关联视觉资产 URL
 * @param assetTitle       关联视觉资产标题
 * @param section          所属章节
 * @param anchorChunkIndex 关联的最近文本 chunk 序号
 */
public record Citation(
        int citationNumber,
        String chunkId,
        UUID documentId,
        String documentTitle,
        String excerpt,
        Integer pageNumber,
        double score,
        String contentType,
        UUID assetId,
        String assetUrl,
        String assetTitle,
        String section,
        Integer anchorChunkIndex
) {}
