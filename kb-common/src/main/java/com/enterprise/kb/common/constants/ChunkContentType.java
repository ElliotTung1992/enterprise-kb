package com.enterprise.kb.common.constants;

/**
 * 文档分块内容类型。
 */
public enum ChunkContentType {
    /** 普通文本分块。 */
    TEXT,
    /** 图片引用分块，记录图片标题、章节、原始路径和处理状态。 */
    IMAGE_REFERENCE,
    /** 图片语义分块，记录 OCR、caption、summary 等视觉理解结果。 */
    IMAGE_CAPTION,
    /** 流程图源码分块，记录 Mermaid/PlantUML 源码和基础说明。 */
    DIAGRAM_SOURCE,
    /** 流程图语义分块，记录 OCR、caption、summary 等视觉理解结果。 */
    DIAGRAM_SUMMARY
}
