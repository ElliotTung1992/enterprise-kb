package com.enterprise.kb.document.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class MdParentChunk {

    private UUID id;
    /** 所属 Markdown 文档 ID */
    private UUID documentId;
    /** 所属知识空间 ID */
    private UUID spaceId;
    /** H1-H3 标题面包屑 */
    private String section;
    /** 当前 parent 对应的标题层级 */
    private Integer headingLevel;
    /** enhancedContent：Section 增强后的 Markdown 正文，保留原文结构并追加图片语义说明 */
    private String content;
    /** Parent 在文档内的顺序 */
    private Integer ordinal;
    /** 该 parent 下的 child 数量 */
    private Integer childCount = 0;
    /** Parent 在原文中的起始字符位置 */
    private Integer charStart;
    /** Parent 在原文中的结束字符位置 */
    private Integer charEnd;
    /** 创建时间 */
    private Instant createdAt = Instant.now();
}
