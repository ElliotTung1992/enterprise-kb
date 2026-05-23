package com.enterprise.kb.document.model;

import com.enterprise.kb.common.constants.ChunkContentType;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class DocumentChunk {

    private UUID id;
    /** 所属文档 ID（与 Milvus Document ID 一致） */
    private UUID documentId;
    /** 内容类型：TEXT / IMAGE_REFERENCE / IMAGE_CAPTION / DIAGRAM_SOURCE / DIAGRAM_SUMMARY */
    private ChunkContentType contentType = ChunkContentType.TEXT;
    /** 关联的视觉资产 ID，普通文本 chunk 为空 */
    private UUID assetId;
    /** 分块序号（同一文档内从 0 开始递增） */
    private Integer chunkIndex;
    /** 文本内容 */
    private String content;
    /** Token 数量 */
    private Integer tokenCount;
    /** Milvus 向量记录 ID（与本表 id 一致） */
    private String milvusId;
    /** 向量化使用的 Embedding 模型名称 */
    private String embeddingModel;
    /** 来源页码（PDF 场景） */
    private Integer pageNumber;
    /** Markdown 所属章节 */
    private String section;
    /** 视觉内容关联的最近文本 chunk 序号 */
    private Integer anchorChunkIndex;
    /** 内容在原文档中的字符起始位置 */
    private Integer charStart;
    /** 内容在原文档中的字符结束位置 */
    private Integer charEnd;
    /** 创建时间 */
    private Instant createdAt = Instant.now();
}
