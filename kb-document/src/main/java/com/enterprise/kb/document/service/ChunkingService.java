package com.enterprise.kb.document.service;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.UUID;

/**
 * Document chunking service that splits documents into smaller pieces.
 */
public interface ChunkingService {

    /**
     * Split a list of documents into chunks with metadata enrichment.
     *
     * @param documents the source documents to chunk
     * @param documentId the parent document ID
     * @param spaceId    the space ID for metadata
     * @return list of chunked documents
     */
    List<Document> chunk(List<Document> documents, UUID documentId, UUID spaceId);
}
