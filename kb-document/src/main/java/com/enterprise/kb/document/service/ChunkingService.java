package com.enterprise.kb.document.service;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.UUID;

/**
 * 文档分块服务接口，将文档拆分为更小的块并注入元数据。
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
