package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.document.service.ChunkMetadataService;
import com.enterprise.kb.document.service.DocumentService;
import com.enterprise.kb.document.service.VectorStoreService;
import com.enterprise.kb.common.constants.DocumentStatus;
import com.enterprise.kb.common.exception.InvalidRequestException;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.document.dto.DocumentDto;
import com.enterprise.kb.document.mapper.DocumentMapper;
import com.enterprise.kb.document.model.Document;
import com.enterprise.kb.document.pipeline.DocumentIngestionPipeline;
import com.enterprise.kb.document.pipeline.IngestionContext;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 文档服务实现，提供文档的上传、查询、删除与重新处理功能。
 * <p>支持 PDF、DOCX、DOC、MD、TXT、HTML 格式上传，上传后触发异步摄取管道。
 * 软删除文档同时清理 Milvus 向量库和 chunk 元数据。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentMapper documentMapper;
    private final DocumentIngestionPipeline ingestionPipeline;
    private final VectorStoreService vectorStoreService;
    private final ChunkMetadataService chunkMetadataService;

    @Value("${enterprise.kb.storage.base-path:./uploads}")
    private String storageBasePath;

    @Value("${enterprise.kb.document.max-file-size-mb:100}")
    private long maxFileSizeMb;

    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "text/markdown", "text/x-markdown",
            "text/plain",
            "text/html"
    );

    /**
     * 上传并摄取文档。
     * <p>保存文件后异步触发摄取管道（解析 → 分块 → 向量化），立即返回 PENDING 状态的文档 DTO。</p>
     *
     * @param spaceId 目标空间 ID
     * @param file    上传的文件
     * @param userId  上传用户 ID
     * @return 文档 DTO
     */
    @Override
    @Transactional
    public DocumentDto uploadDocument(UUID spaceId, MultipartFile file, UUID userId) {
        validateFile(file);
        String filePath = saveFile(file, spaceId);
        String mimeType = detectMimeType(file);

        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        doc.setSpaceId(spaceId);
        doc.setUploadedBy(userId);
        doc.setTitle(sanitizeFilename(file.getOriginalFilename()));
        doc.setOriginalFilename(file.getOriginalFilename());
        doc.setFilePath(filePath);
        doc.setFileSizeBytes(file.getSize());
        doc.setMimeType(mimeType);
        doc.setStatus(DocumentStatus.PENDING);
        documentMapper.insert(doc);

        ingestionPipeline.ingest(IngestionContext.builder()
                .documentId(doc.getId())
                .spaceId(spaceId)
                .filePath(filePath)
                .mimeType(mimeType)
                .build());

        return toDto(doc);
    }

    /**
     * 根据 ID 获取文档。
     *
     * @param docId 文档 UUID
     * @return 文档 DTO
     */
    @Override
    @Transactional(readOnly = true)
    public DocumentDto getDocument(UUID docId) {
        return toDto(findActive(docId));
    }

    /**
     * 分页查询文档列表。
     *
     * @param spaceId 空间 UUID
     * @param status  状态过滤（可选）
     * @param keyword 关键词过滤（可选）
     * @param page    页码（从 0 开始）
     * @param size    每页大小
     * @return 分页文档 DTO 列表
     */
    @Override
    @Transactional(readOnly = true)
    public PageInfo<DocumentDto> listDocuments(UUID spaceId, DocumentStatus status,
                                                String keyword, int page, int size) {
        PageHelper.startPage(page + 1, size);
        List<Document> docs = documentMapper.findBySpaceId(spaceId, status, keyword);
        PageInfo<Document> docPage = new PageInfo<>(docs);
        PageInfo<DocumentDto> result = new PageInfo<>();
        result.setList(docs.stream().map(this::toDto).toList());
        result.setTotal(docPage.getTotal());
        result.setPages(docPage.getPages());
        result.setPageNum(docPage.getPageNum());
        result.setPageSize(docPage.getPageSize());
        return result;
    }

    /**
     * 软删除文档。
     * <p>同时从 Milvus 向量库和 chunk 表中清理关联数据。</p>
     *
     * @param docId 文档 UUID
     */
    @Override
    @Transactional
    public void deleteDocument(UUID docId) {
        Document doc = findActive(docId);
        doc.setDeletedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        documentMapper.update(doc);
        vectorStoreService.deleteByDocumentId(docId);
        chunkMetadataService.deleteByDocumentId(docId);
    }

    /**
     * 重新处理文档。
     * <p>重置状态为 PENDING 并重新触发摄取管道。</p>
     *
     * @param docId 文档 UUID
     * @return 更新后的文档 DTO
     */
    @Override
    @Transactional
    public DocumentDto reprocessDocument(UUID docId) {
        Document doc = findActive(docId);
        doc.setStatus(DocumentStatus.PENDING);
        doc.setErrorMessage(null);
        doc.setChunkCount(0);
        doc.setUpdatedAt(Instant.now());
        documentMapper.update(doc);
        ingestionPipeline.ingest(IngestionContext.builder()
                .documentId(doc.getId())
                .spaceId(doc.getSpaceId())
                .filePath(doc.getFilePath())
                .mimeType(doc.getMimeType())
                .build());
        return toDto(doc);
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) throw new InvalidRequestException("File is empty");
        if (file.getSize() > maxFileSizeMb * 1024 * 1024)
            throw new InvalidRequestException("File exceeds maximum size of " + maxFileSizeMb + " MB");
        String mimeType = detectMimeType(file);
        if (!ALLOWED_MIME_TYPES.contains(mimeType))
            throw new InvalidRequestException("Unsupported file type: " + mimeType);
    }

    private String detectMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) return contentType;
        String name = file.getOriginalFilename();
        if (name != null) {
            if (name.endsWith(".md") || name.endsWith(".markdown")) return "text/markdown";
            if (name.endsWith(".pdf")) return "application/pdf";
            if (name.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            if (name.endsWith(".doc")) return "application/msword";
            if (name.endsWith(".txt")) return "text/plain";
            if (name.endsWith(".html") || name.endsWith(".htm")) return "text/html";
        }
        return "application/octet-stream";
    }

    private String saveFile(MultipartFile file, UUID spaceId) {
        try {
            Path dir = Paths.get(storageBasePath, spaceId.toString());
            Files.createDirectories(dir);
            String filename = UUID.randomUUID() + "_" + sanitizeFilename(file.getOriginalFilename());
            Path dest = dir.resolve(filename);
            file.transferTo(dest);
            return dest.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }

    private String sanitizeFilename(String name) {
        if (!StringUtils.hasText(name)) return "unknown";
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private Document findActive(UUID id) {
        return documentMapper.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
    }

    private DocumentDto toDto(Document d) {
        return new DocumentDto(
                d.getId(), d.getSpaceId(), d.getUploadedBy(),
                d.getTitle(), d.getOriginalFilename(), d.getMimeType(),
                d.getFileSizeBytes(), d.getStatus(), d.getErrorMessage(),
                d.getChunkCount(), d.getLanguage(), d.getMetadata(),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
