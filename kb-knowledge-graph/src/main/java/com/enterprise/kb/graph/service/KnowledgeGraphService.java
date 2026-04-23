package com.enterprise.kb.graph.service;

import com.enterprise.kb.graph.dto.GraphDto;

import java.util.UUID;

/**
 * 知识图谱服务接口，构建空间内文档的关系图谱视图。
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
