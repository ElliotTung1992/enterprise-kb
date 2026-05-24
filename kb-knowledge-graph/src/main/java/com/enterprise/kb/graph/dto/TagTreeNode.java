package com.enterprise.kb.graph.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 标签树节点 DTO。
 *
 * @param id       标签 ID
 * @param name     标签名称
 * @param slug     标签标识
 * @param tagType  标签类型
 * @param color    标签颜色
 * @param parentId 父标签 ID
 * @param depth    树层级深度
 * @param children 子标签节点
 */
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
