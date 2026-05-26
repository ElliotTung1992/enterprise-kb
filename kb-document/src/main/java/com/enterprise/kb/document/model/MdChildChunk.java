package com.enterprise.kb.document.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class MdChildChunk {

    private UUID id;
    /** 所属 parent chunk ID，同时用于命中后回查完整 section */
    private UUID parentId;
    /** 所属 Markdown 文档 ID */
    private UUID documentId;
    /** 所属知识空间 ID */
    private UUID spaceId;
    /** 冗余的 H1-H3 标题面包屑，便于检索展示 */
    private String section;
    /** Child 在 parent 内的顺序，从 0 开始 */
    private Integer seqInParent;
    /** 用于 embedding 和关键词检索的文本，表格会被自然语言化 */
    private String embedText;
    /** 粗略 token 数，用于调试切分效果 */
    private Integer tokenCount;
    /** 入库时使用的 embedding 模型名称 */
    private String embeddingModel;
    /** Child 对应 parent 原文区间的起始字符位置 */
    private Integer charStart;
    /** Child 对应 parent 原文区间的结束字符位置 */
    private Integer charEnd;
    /** 创建时间 */
    private Instant createdAt = Instant.now();
}
