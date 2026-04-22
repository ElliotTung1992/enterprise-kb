package com.enterprise.kb.document.service;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.UUID;

/**
 * Vector store service for upserting and deleting document vectors.
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
}
