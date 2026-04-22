package com.enterprise.kb.document.service;

import com.enterprise.kb.document.dto.AddRelationRequest;
import com.enterprise.kb.document.dto.DocumentRelationDto;

import java.util.List;
import java.util.UUID;

/**
 * Document relation service for managing relationships between documents.
 */
public interface DocumentRelationService {

    /**
     * Add a relation between two documents.
     *
     * @param sourceDocId the source document ID
     * @param req         the relation request
     * @param userId      the user performing the action
     * @return the created relation DTO
     */
    DocumentRelationDto addRelation(UUID sourceDocId, AddRelationRequest req, UUID userId);

    /**
     * Get all relations for a document.
     *
     * @param documentId the document ID
     * @return list of relation DTOs
     */
    List<DocumentRelationDto> getRelations(UUID documentId);

    /**
     * Delete a relation by ID.
     *
     * @param relationId the relation ID to delete
     */
    void deleteRelation(UUID relationId);
}
