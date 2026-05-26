package com.enterprise.kb.document.controller;

import com.enterprise.kb.common.constants.DocumentStatus;
import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.document.dto.MdDocumentDto;
import com.enterprise.kb.document.service.MdDocumentService;
import com.enterprise.kb.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Markdown 结构化文档控制器。
 */
@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/md-documents")
@RequiredArgsConstructor
public class MdDocumentController {

    private final MdDocumentService mdDocumentService;
    private final UserService userService;

    /**
     * 分页查询 Markdown 文档。
     *
     * @param spaceId 空间 ID
     * @param status  状态过滤
     * @param keyword 标题关键词
     * @param page    页码
     * @param size    每页大小
     * @return 分页 Markdown 文档
     */
    @GetMapping
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<PageResponse<MdDocumentDto>>> listDocuments(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.of(mdDocumentService.listDocuments(spaceId, status, keyword, page, size))));
    }

    /**
     * 上传 Markdown 文档并启动结构化入库。
     *
     * @param spaceId     空间 ID
     * @param file        Markdown 文件
     * @param userDetails 当前用户
     * @return 初始文档元数据
     */
    @PostMapping("/upload")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'EDITOR')")
    public ResponseEntity<ApiResponse<MdDocumentDto>> upload(
            @PathVariable UUID spaceId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userService.getUserIdByUsername(userDetails.getUsername());
        MdDocumentDto document = mdDocumentService.uploadDocument(spaceId, file, userId);
        return ResponseEntity.status(202).body(ApiResponse.ok(document, "Markdown file accepted, processing started"));
    }

    /**
     * 查询 Markdown 文档详情。
     *
     * @param spaceId    空间 ID
     * @param documentId 文档 ID
     * @return 文档详情
     */
    @GetMapping("/{documentId}")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<MdDocumentDto>> getDocument(
            @PathVariable UUID spaceId,
            @PathVariable UUID documentId) {
        return ResponseEntity.ok(ApiResponse.ok(mdDocumentService.getDocument(spaceId, documentId)));
    }

    /**
     * 删除 Markdown 文档。
     *
     * @param spaceId    空间 ID
     * @param documentId 文档 ID
     * @return 删除结果
     */
    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'EDITOR')")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @PathVariable UUID spaceId,
            @PathVariable UUID documentId) {
        mdDocumentService.deleteDocument(spaceId, documentId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Markdown document deleted"));
    }
}
