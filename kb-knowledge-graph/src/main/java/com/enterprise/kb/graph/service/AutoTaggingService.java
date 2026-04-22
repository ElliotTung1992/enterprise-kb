package com.enterprise.kb.graph.service;

import java.util.UUID;

/**
 * Auto-tagging service that uses AI to suggest and apply tags to documents.
 */
public interface AutoTaggingService {

    /**
     * Asynchronously suggest and apply relevant tags to a document based on its content.
     * Tags with confidence below 0.6 are ignored. Only tags already present in the space are suggested.
     *
     * @param documentId    the document ID to tag
     * @param spaceId       the space ID
     * @param documentTitle  the title of the document
     * @param excerpt       the content excerpt to analyze
     */
    void suggestAndApplyTags(UUID documentId, UUID spaceId, String documentTitle, String excerpt);
}
