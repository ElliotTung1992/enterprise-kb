package com.enterprise.kb.graph.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.util.SecurityUtils;
import com.enterprise.kb.graph.dto.*;
import com.enterprise.kb.graph.service.KnowledgeGraphService;
import com.enterprise.kb.graph.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 标签与知识图谱控制器
 * <p>提供指定知识空间内标签的增删查、层级树查询、标签合并，以及知识图谱视图接口。
 * 所有接口均通过 {@code @PreAuthorize} 进行空间级别的权限校验。</p>
 */
@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;
    private final KnowledgeGraphService knowledgeGraphService;

    /**
     * 查询空间内所有标签（平铺列表）
     *
     * @param spaceId 空间 ID
     * @return 该空间下所有标签的 DTO 列表
     */
    @GetMapping
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<List<TagDto>>> listTags(@PathVariable UUID spaceId) {
        return ResponseEntity.ok(ApiResponse.ok(tagService.listTags(spaceId)));
    }

    /**
     * 查询标签层级树
     * <p>使用递归 CTE 一次查询构建完整的父子层级结构，适用于前端树形组件渲染。</p>
     *
     * @param spaceId 空间 ID
     * @return 顶层标签节点列表，每个节点递归包含子节点
     */
    @GetMapping("/tree")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<List<TagTreeNode>>> getTree(@PathVariable UUID spaceId) {
        return ResponseEntity.ok(ApiResponse.ok(tagService.getTagTree(spaceId)));
    }

    /**
     * 创建新标签
     * <p>可指定父标签 ID 创建子标签，支持 TAG / CATEGORY / ENTITY / TOPIC 类型。</p>
     *
     * @param spaceId 空间 ID
     * @param req     创建请求体，包含标签名称、类型和可选的父标签 ID
     * @return HTTP 201 及新建标签的 DTO
     */
    @PostMapping
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'EDITOR')")
    public ResponseEntity<ApiResponse<TagDto>> createTag(
            @PathVariable UUID spaceId,
            @Valid @RequestBody CreateTagRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.ok(tagService.createTag(spaceId, req)));
    }

    /**
     * 更新标签基本信息
     *
     * @param spaceId 空间 ID
     * @param tagId   标签 ID
     * @param req     更新请求体
     * @return 更新后的标签 DTO
     */
    @PutMapping("/{tagId}")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'EDITOR')")
    public ResponseEntity<ApiResponse<TagDto>> updateTag(
            @PathVariable UUID spaceId,
            @PathVariable UUID tagId,
            @Valid @RequestBody UpdateTagRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(tagService.updateTag(tagId, req)));
    }

    /**
     * 删除标签
     * <p>删除指定标签，同时解除与文档的关联（document_tags 记录）。</p>
     *
     * @param spaceId 空间 ID
     * @param tagId   待删除的标签 ID
     * @return 删除成功提示
     */
    @DeleteMapping("/{tagId}")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'EDITOR')")
    public ResponseEntity<ApiResponse<Void>> deleteTag(
            @PathVariable UUID spaceId, @PathVariable UUID tagId) {
        tagService.deleteTag(tagId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Tag deleted"));
    }

    /**
     * 查询标签关联的文档列表
     *
     * @param spaceId 空间 ID
     * @param tagId   标签 ID
     * @return 文档摘要 DTO 列表
     */
    @GetMapping("/{tagId}/documents")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<List<TagDocumentDto>>> getTagDocuments(
            @PathVariable UUID spaceId, @PathVariable UUID tagId) {
        return ResponseEntity.ok(ApiResponse.ok(tagService.getTagDocuments(tagId)));
    }

    /**
     * 添加文档到标签
     *
     * @param spaceId    空间 ID
     * @param tagId      标签 ID
     * @param documentId 文档 ID
     * @return 关联成功提示
     */
    @PostMapping("/{tagId}/documents/{documentId}")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'EDITOR')")
    public ResponseEntity<ApiResponse<Void>> addDocumentToTag(
            @PathVariable UUID spaceId,
            @PathVariable UUID tagId,
            @PathVariable UUID documentId) {
        tagService.addDocumentToTag(tagId, documentId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.status(201).body(ApiResponse.ok(null, "Document added to tag"));
    }

    /**
     * 从标签移除文档
     *
     * @param spaceId    空间 ID
     * @param tagId      标签 ID
     * @param documentId 文档 ID
     * @return 移除成功提示
     */
    @DeleteMapping("/{tagId}/documents/{documentId}")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'EDITOR')")
    public ResponseEntity<ApiResponse<Void>> removeDocumentFromTag(
            @PathVariable UUID spaceId,
            @PathVariable UUID tagId,
            @PathVariable UUID documentId) {
        tagService.removeDocumentFromTag(tagId, documentId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Document removed from tag"));
    }

    /**
     * 合并标签
     * <p>将源标签（{@code tagId}）的所有文档关联迁移到目标标签（{@code targetTagId}），
     * 迁移完成后删除源标签。需要空间 ADMIN 权限。</p>
     *
     * @param spaceId     空间 ID
     * @param tagId       源标签 ID（将被删除）
     * @param targetTagId 目标标签 ID（保留，继承源标签的所有文档关联）
     * @return 合并成功提示
     */
    @PostMapping("/{tagId}/merge/{targetTagId}")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> mergeTags(
            @PathVariable UUID spaceId,
            @PathVariable UUID tagId,
            @PathVariable UUID targetTagId) {
        tagService.mergeTags(tagId, targetTagId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Tags merged"));
    }

    /**
     * 获取知识图谱视图
     * <p>构建该空间内的文档关系图：包含显式关系边（REFERENCES / RELATED_TO 等）
     * 和基于共享标签推断的隐式关联，返回节点与边的图结构 DTO。</p>
     *
     * @param spaceId 空间 ID
     * @return 图谱 DTO，含节点（文档）和边（关系）列表
     */
    @GetMapping("/graph")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<GraphDto>> getGraph(@PathVariable UUID spaceId) {
        return ResponseEntity.ok(ApiResponse.ok(knowledgeGraphService.buildGraphView(spaceId)));
    }
}
