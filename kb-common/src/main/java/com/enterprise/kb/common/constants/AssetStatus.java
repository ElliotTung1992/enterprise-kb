package com.enterprise.kb.common.constants;

/**
 * 文档视觉资产处理状态。
 */
public enum AssetStatus {
    /** 已创建资产记录，等待异步视觉处理。 */
    PENDING,
    /** 视觉处理进行中。 */
    PROCESSING,
    /** OCR/caption/summary 等视觉处理已完成。 */
    READY,
    /** 视觉处理失败，达到重试上限或遇到不可恢复错误。 */
    FAILED,
    /** 人工修正后等待重新生成 chunk 和向量。 */
    REINDEX_PENDING,
    /** 正在基于人工修正内容重新生成 chunk 和向量。 */
    REINDEXING
}
