package com.enterprise.kb.document.controller;

import com.enterprise.kb.common.constants.DocumentStatus;
import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.document.dto.AddRelationRequest;
import com.enterprise.kb.document.dto.AssetCorrectionRequest;
import com.enterprise.kb.document.dto.AssetUrlResponse;
import com.enterprise.kb.document.dto.DocumentAssetDto;
import com.enterprise.kb.document.dto.DocumentDto;
import com.enterprise.kb.document.dto.DocumentRelationDto;
import com.enterprise.kb.document.service.DocumentAssetService;
import com.enterprise.kb.document.service.DocumentRelationService;
import com.enterprise.kb.document.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * 文档管理控制器
 * <p>提供指定知识空间内文档的上传、查询、删除、重新处理及关系管理等接口。
 * 所有接口均通过 {@code @PreAuthorize} 进行空间级别的权限校验。</p>
 */
@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentRelationService relationService;
    private final DocumentAssetService assetService;
    private final com.enterprise.kb.user.service.UserService userService;

    /**
     * 分页查询文档列表
     * <p>支持按状态和关键词过滤，仅返回未软删除的文档。</p>
     *
     * @param spaceId 空间 ID
     * @param status  文档状态过滤（可选）：PENDING / PROCESSING / READY / FAILED
     * @param keyword 标题关键词模糊搜索（可选）
     * @param page    页码（从 0 开始，默认 0）
     * @param size    每页条数（默认 20）
     * @return 分页的文档 DTO 列表
     */
    @GetMapping
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<PageResponse<DocumentDto>>> listDocuments(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.of(documentService.listDocuments(spaceId, status, keyword, page, size))));
    }

    /**
     * 上传单个文档
     * <p>保存文件后异步触发摄取管道（解析 → 分块 → 向量化），立即返回 202 Accepted。
     * 客户端应轮询 {@code GET /{docId}} 直到 status 变为 READY。</p>
     *
     * @param spaceId     空间 ID
     * @param file        上传的文件（支持 PDF、DOCX、DOC、MD、TXT、HTML）
     * @param userDetails 当前登录用户（用于记录上传人）
     * @return HTTP 202 及文档初始元数据
     */
    @PostMapping("/upload")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'EDITOR')")
    public ResponseEntity<ApiResponse<DocumentDto>> upload(
            @PathVariable UUID spaceId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userService.getUserIdByUsername(userDetails.getUsername());
        DocumentDto doc = documentService.uploadDocument(spaceId, file, userId);
        return ResponseEntity.status(202).body(ApiResponse.ok(doc, "File accepted, processing started"));
    }

    /**
     * 批量上传文档
     * <p>每个文件独立触发异步摄取管道，全部立即返回 202 Accepted。</p>
     *
     * @param spaceId     空间 ID
     * @param files       文件数组
     * @param userDetails 当前登录用户
     * @return HTTP 202 及各文档的初始元数据列表
     */
    @PostMapping("/upload/batch")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'EDITOR')")
    public ResponseEntity<ApiResponse<List<DocumentDto>>> uploadBatch(
            @PathVariable UUID spaceId,
            @RequestParam("files") MultipartFile[] files,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userService.getUserIdByUsername(userDetails.getUsername());
        List<DocumentDto> docs = java.util.Arrays.stream(files)
                .map(f -> documentService.uploadDocument(spaceId, f, userId))
                .toList();
        return ResponseEntity.status(202).body(ApiResponse.ok(docs, "Files accepted, processing started"));
    }

    /**
     * 查询单个文档详情
     *
     * @param spaceId 空间 ID
     * @param docId   文档 ID
     * @return 文档 DTO，包含状态、分块数、元数据等字段
     */
    @GetMapping("/{docId}")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<DocumentDto>> getDocument(
            @PathVariable UUID spaceId, @PathVariable UUID docId) {
        return ResponseEntity.ok(ApiResponse.ok(documentService.getDocument(docId)));
    }

    /**
     * 获取文档全文内容
     * <p>将文档所有分块按顺序拼接返回，超过 10000 字时截断并附提示。主要供 MCP 工具调用。</p>
     *
     * @param spaceId 空间 ID
     * @param docId   文档 ID
     * @return 文档全文文本
     */
    @GetMapping("/{docId}/content")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<String>> getDocumentContent(
            @PathVariable UUID spaceId, @PathVariable UUID docId) {
        return ResponseEntity.ok(ApiResponse.ok(documentService.getDocumentContent(spaceId, docId)));
    }

    /**
     * 软删除文档
     * <p>设置 deleted_at 时间戳，同时从 Milvus 向量库中删除对应 chunk 向量。</p>
     *
     * @param spaceId 空间 ID
     * @param docId   待删除的文档 ID
     * @return 删除成功提示
     */
    @DeleteMapping("/{docId}")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'EDITOR')")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @PathVariable UUID spaceId, @PathVariable UUID docId) {
        documentService.deleteDocument(docId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Document deleted"));
    }

    /**
     * 重新处理文档
     * <p>清除旧的 chunk 数据后重新触发摄取管道，适用于文档解析失败或更新模型后需要重新向量化的场景。</p>
     *
     * @param spaceId 空间 ID
     * @param docId   待重新处理的文档 ID
     * @return HTTP 202 及文档当前元数据
     */
    @PostMapping("/{docId}/reprocess")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'EDITOR')")
    public ResponseEntity<ApiResponse<DocumentDto>> reprocess(
            @PathVariable UUID spaceId, @PathVariable UUID docId) {
        return ResponseEntity.accepted().body(ApiResponse.ok(
                documentService.reprocessDocument(docId), "Reprocessing started"));
    }

    /**
     * 查询文档的关联关系列表
     * <p>返回该文档作为源或目标的所有显式关系（REFERENCES、RELATED_TO 等）。</p>
     *
     * @param spaceId 空间 ID
     * @param docId   文档 ID
     * @return 文档关系 DTO 列表
     */
    @GetMapping("/{docId}/relations")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<List<DocumentRelationDto>>> getRelations(
            @PathVariable UUID spaceId, @PathVariable UUID docId) {
        return ResponseEntity.ok(ApiResponse.ok(relationService.getRelations(docId)));
    }

    /**
     * 查询文档视觉资产列表
     * <p>返回 Markdown 入库时抽取出的图片和流程图资产。</p>
     *
     * @param spaceId 空间 ID
     * @param docId   文档 ID
     * @return 视觉资产列表
     */
    @GetMapping("/{docId}/assets")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<List<DocumentAssetDto>>> listAssets(
            @PathVariable UUID spaceId, @PathVariable UUID docId) {
        return ResponseEntity.ok(ApiResponse.ok(assetService.listAssets(spaceId, docId)));
    }

    /**
     * 查询文档视觉资产详情
     *
     * @param spaceId 空间 ID
     * @param docId   文档 ID
     * @param assetId 资产 ID
     * @return 视觉资产详情
     */
    @GetMapping("/{docId}/assets/{assetId}")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<DocumentAssetDto>> getAsset(
            @PathVariable UUID spaceId, @PathVariable UUID docId, @PathVariable UUID assetId) {
        return ResponseEntity.ok(ApiResponse.ok(assetService.getAsset(spaceId, docId, assetId)));
    }

    /**
     * 获取视觉资产内容访问 URL
     * <p>后端完成空间权限校验后，返回短期有效的 MinIO 预签名 URL。</p>
     *
     * @param spaceId 空间 ID
     * @param docId   文档 ID
     * @param assetId 资产 ID
     * @return 短期访问 URL
     */
    @GetMapping("/{docId}/assets/{assetId}/content")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<AssetUrlResponse>> getAssetContentUrl(
            @PathVariable UUID spaceId, @PathVariable UUID docId, @PathVariable UUID assetId) {
        return ResponseEntity.ok(ApiResponse.ok(assetService.createContentUrl(spaceId, docId, assetId)));
    }

    /**
     * 人工修正视觉资产描述和摘要
     * <p>保存修正内容后，资产会进入 REINDEX_PENDING 状态，等待 worker 异步重建视觉 chunk 和向量。</p>
     *
     * @param spaceId 空间 ID
     * @param docId   文档 ID
     * @param assetId 资产 ID
     * @param request 修正请求
     * @return 更新后的视觉资产详情
     */
    @PatchMapping("/{docId}/assets/{assetId}/correction")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'EDITOR')")
    public ResponseEntity<ApiResponse<DocumentAssetDto>> correctAsset(
            @PathVariable UUID spaceId,
            @PathVariable UUID docId,
            @PathVariable UUID assetId,
            @RequestBody AssetCorrectionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(assetService.correctAsset(spaceId, docId, assetId, request)));
    }

    /**
     * 手动添加文档关系
     * <p>在两个文档之间创建一条有向关系边，关系类型由请求体中的 relationType 指定。</p>
     *
     * @param spaceId     空间 ID
     * @param docId       源文档 ID
     * @param req         关系请求体，包含目标文档 ID 和关系类型
     * @param userDetails 当前登录用户（用于记录创建人）
     * @return HTTP 201 及新建的关系 DTO
     */
    @PostMapping("/{docId}/relations")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'EDITOR')")
    public ResponseEntity<ApiResponse<DocumentRelationDto>> addRelation(
            @PathVariable UUID spaceId,
            @PathVariable UUID docId,
            @Valid @RequestBody AddRelationRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userService.getUserIdByUsername(userDetails.getUsername());
        return ResponseEntity.status(201).body(ApiResponse.ok(relationService.addRelation(docId, req, userId)));
    }
}
