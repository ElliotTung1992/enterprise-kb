package com.enterprise.kb.document.service;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.UUID;

/**
 * Markdown 专用向量存储服务。
 */
public interface MdVectorStoreService {

    /**
     * 批量写入 Markdown child 向量。
     *
     * @param documents 向量文档列表
     */
    void upsert(List<Document> documents);

    /**
     * 删除指定 Markdown 文档的向量。
     *
     * @param documentId 文档 ID
     */
    void deleteByDocumentId(UUID documentId);
}
