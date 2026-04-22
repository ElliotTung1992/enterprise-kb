package com.enterprise.kb.graph.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record TagTreeNode(
        UUID id,
        String name,
        String slug,
        String tagType,
        String color,
        UUID parentId,
        int depth,
        List<TagTreeNode> children
) {
    public TagTreeNode(UUID id, String name, String slug, String tagType,
                       String color, UUID parentId, int depth) {
        this(id, name, slug, tagType, color, parentId, depth, new ArrayList<>());
    }
}
