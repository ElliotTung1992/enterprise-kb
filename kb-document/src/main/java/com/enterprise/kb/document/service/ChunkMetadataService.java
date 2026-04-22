package com.enterprise.kb.document.service;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.UUID;

/**
 * Chunk metadata persistence service for storing and managing document chunk data.
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
