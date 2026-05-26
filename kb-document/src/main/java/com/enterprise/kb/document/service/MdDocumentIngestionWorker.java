package com.enterprise.kb.document.service;

import java.util.UUID;

/**
 * Markdown 文档异步入库 Worker。
 */
public interface MdDocumentIngestionWorker {

    /**
     * 异步执行 Markdown 结构化入库。
     *
     * @param documentId 文档 ID
     */
    void ingest(UUID documentId);
}
