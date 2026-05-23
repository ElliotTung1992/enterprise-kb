package com.enterprise.kb.common.constants;

/**
 * 文档入库处理状态。
 */
public enum DocumentStatus {
    /** 文档已创建，等待异步入库处理。 */
    PENDING,
    /** 文档正在解析、分块、向量化或写入检索索引。 */
    PROCESSING,
    /** 文档入库完成，可被检索和问答引用。 */
    READY,
    /** 文档文本已可用，但仍有视觉资产等待异步 OCR/caption 处理。 */
    READY_WITH_PENDING_ASSETS,
    /** 文档文本已可用，但部分视觉资产处理失败。 */
    READY_WITH_ASSET_ERRORS,
    /** 文档入库失败，需要查看 errorMessage 或重新处理。 */
    FAILED
}
