package com.enterprise.kb.graph.service;

import com.enterprise.kb.graph.dto.GraphDto;

import java.util.UUID;

/**
 * Knowledge graph service for building document relationship graphs.
 */
public interface KnowledgeGraphService {

    /**
     * Build a graph view of all documents in a space showing relationships and shared tags.
     *
     * @param spaceId the space ID
     * @return the graph DTO containing nodes and edges
     */
    GraphDto buildGraphView(UUID spaceId);
}
