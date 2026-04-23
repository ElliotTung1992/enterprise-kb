package com.enterprise.kb.document.service;

import com.enterprise.kb.common.constants.DocumentStatus;
import com.enterprise.kb.document.dto.DocumentDto;
import com.github.pagehelper.PageInfo;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * 文档服务接口，提供文档的创建、查询、删除与重新处理功能。
 */
public interface DocumentService {

    /**
     * Upload and ingest a new document.
     *
     * @param spaceId the target space ID
     * @param file    the uploaded file
     * @param userId  the ID of the user performing the upload
     * @return the created document DTO
     */
    DocumentDto uploadDocument(UUID spaceId, MultipartFile file, UUID userId);

    /**
     * Get a document by ID.
     *
     * @param docId the document ID
     * @return the document DTO
     */
    DocumentDto getDocument(UUID docId);

    /**
     * List documents in a space with optional filtering and pagination.
     *
     * @param spaceId the space ID
     * @param status  optional document status filter
     * @param keyword optional keyword filter
     * @param page    page number (0-indexed)
     * @param size    page size
     * @return paginated list of document DTOs
     */
    PageInfo<DocumentDto> listDocuments(UUID spaceId, DocumentStatus status, String keyword, int page, int size);

    /**
     * Soft-delete a document and clean up its vectors and chunks.
     *
     * @param docId the document ID to delete
     */
    void deleteDocument(UUID docId);

    /**
     * Reprocess a document that is in PENDING or ERROR state.
     *
     * @param docId the document ID to reprocess
     * @return the updated document DTO
     */
    DocumentDto reprocessDocument(UUID docId);
}
