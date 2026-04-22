package com.enterprise.kb.graph.dto;

import java.util.List;
import java.util.UUID;

public record GraphDto(
        List<GraphNode> nodes,
        List<GraphEdge> edges
) {
    public record GraphNode(UUID id, String title, List<String> tags, int chunkCount) {}
    public record GraphEdge(UUID sourceId, UUID targetId, String relationType, double weight) {}
}
