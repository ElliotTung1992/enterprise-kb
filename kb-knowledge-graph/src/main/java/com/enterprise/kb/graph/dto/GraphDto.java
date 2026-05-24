package com.enterprise.kb.graph.dto;

import java.util.List;
import java.util.UUID;

/**
 * 知识图谱 DTO。
 *
 * @param nodes 图谱节点列表
 * @param edges 图谱关系边列表
 */
public record GraphDto(
        List<GraphNode> nodes,
        List<GraphEdge> edges
) {
    /**
     * 知识图谱节点。
     *
     * @param id         文档 ID
     * @param title      文档标题
     * @param tags       标签名称列表
     * @param chunkCount 文档分块数量
     */
    public record GraphNode(UUID id, String title, List<String> tags, int chunkCount) {}
    /**
     * 知识图谱关系边。
     *
     * @param sourceId     源文档 ID
     * @param targetId     目标文档 ID
     * @param relationType 关系类型
     * @param weight       关系权重
     */
    public record GraphEdge(UUID sourceId, UUID targetId, String relationType, double weight) {}
}
