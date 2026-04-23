package com.enterprise.kb.document.service;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.UUID;

/**
 * Chunk 元数据持久化服务接口，负责文档分块数据的存储与管理。
 */
public interface ChunkMetadataService {

    /**
     * Save chunks for a document, replacing any existing chunks.
     *
     * @param documentId    the parent document ID
     * @param chunks        the list of Spring AI documents (chunks)
     * @param embeddingModel the embedding model used
     */
    void saveChunks(UUID documentId, List<Document> chunks, String embeddingModel);

    /**
     * Delete all chunks associated with a document.
     *
     * @param documentId the document ID
     */
    void deleteByDocumentId(UUID documentId);
}
