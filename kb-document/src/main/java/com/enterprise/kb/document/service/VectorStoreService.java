package com.enterprise.kb.document.service;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.UUID;

/**
 * 向量存储服务接口，负责文档块向量的写入与删除。
 */
public interface VectorStoreService {

    /**
     * Upsert a list of documents into the vector store in batches.
     *
     * @param documents the documents to upsert
     */
    void upsert(List<Document> documents);

    /**
     * Delete all vectors associated with a document.
     *
     * @param documentId the document ID
     */
    void deleteByDocumentId(UUID documentId);

    /**
     * 删除指定视觉资产生成的向量。
     *
     * @param assetId 资产 ID
     */
    void deleteByAssetId(UUID assetId);
}
