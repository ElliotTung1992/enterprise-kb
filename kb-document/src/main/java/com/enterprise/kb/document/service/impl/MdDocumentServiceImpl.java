package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.common.constants.DocumentStatus;
import com.enterprise.kb.common.event.SpaceDeletedEvent;
import com.enterprise.kb.common.exception.InvalidRequestException;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.document.dto.MdDocumentDto;
import com.enterprise.kb.document.mapper.MdChildChunkMapper;
import com.enterprise.kb.document.mapper.MdDocumentAssetMapper;
import com.enterprise.kb.document.mapper.MdDocumentMapper;
import com.enterprise.kb.document.mapper.MdParentChunkMapper;
import com.enterprise.kb.document.model.MdDocument;
import com.enterprise.kb.document.service.DocumentObjectStorageService;
import com.enterprise.kb.document.service.MdDocumentIngestionWorker;
import com.enterprise.kb.document.service.MdDocumentService;
import com.enterprise.kb.document.service.MdVectorStoreService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Markdown 文档服务实现。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MdDocumentServiceImpl implements MdDocumentService {

    private static final List<String> ALLOWED_MIME_TYPES = List.of("text/markdown", "text/x-markdown");

    private final MdDocumentMapper documentMapper;
    private final MdDocumentAssetMapper assetMapper;
    private final MdParentChunkMapper parentChunkMapper;
    private final MdChildChunkMapper childChunkMapper;
    private final MdVectorStoreService vectorStoreService;
    private final MdDocumentIngestionWorker ingestionWorker;
    private final DocumentObjectStorageService objectStorageService;

    @Value("${enterprise.kb.document.max-file-size-mb:100}")
    private long maxFileSizeMb;

    /**
     * 上传 Markdown 文档并异步触发结构化入库。
     *
     * @param spaceId 空间 ID
     * @param file    Markdown 文件
     * @param userId  上传用户 ID
     * @return 文档 DTO
     */
    @Override
    @Transactional
    public MdDocumentDto uploadDocument(UUID spaceId, MultipartFile file, UUID userId) {
        validateFile(file);
        MdDocument document = new MdDocument();
        document.setId(UUID.randomUUID());
        document.setSpaceId(spaceId);
        document.setUploadedBy(userId);
        document.setTitle(sanitizeFilename(file.getOriginalFilename()));
        document.setOriginalFilename(file.getOriginalFilename());
        document.setFileSizeBytes(file.getSize());
        document.setMimeType(detectMimeType(file));
        document.setObjectKey(uploadToObjectStorage(file, document));
        document.setStatus(DocumentStatus.PENDING);
        documentMapper.insert(document);
        ingestionWorker.ingest(document.getId());
        return toDto(document);
    }

    /**
     * 查询 Markdown 文档详情。
     *
     * @param spaceId    空间 ID
     * @param documentId 文档 ID
     * @return 文档 DTO
     */
    @Override
    @Transactional(readOnly = true)
    public MdDocumentDto getDocument(UUID spaceId, UUID documentId) {
        return toDto(findActive(spaceId, documentId));
    }

    /**
     * 分页查询 Markdown 文档。
     *
     * @param spaceId 空间 ID
     * @param status  状态过滤
     * @param keyword 标题关键词
     * @param page    页码
     * @param size    每页大小
     * @return 分页结果
     */
    @Override
    @Transactional(readOnly = true)
    public PageInfo<MdDocumentDto> listDocuments(UUID spaceId, DocumentStatus status, String keyword, int page, int size) {
        PageHelper.startPage(page + 1, size);
        List<MdDocument> documents = documentMapper.findBySpaceId(spaceId, status, keyword);
        PageInfo<MdDocument> documentPage = new PageInfo<>(documents);
        PageInfo<MdDocumentDto> result = new PageInfo<>();
        result.setList(documents.stream().map(this::toDto).toList());
        result.setTotal(documentPage.getTotal());
        result.setPages(documentPage.getPages());
        result.setPageNum(documentPage.getPageNum());
        result.setPageSize(documentPage.getPageSize());
        return result;
    }

    /**
     * 删除 Markdown 文档及其 parent/child/向量数据。
     *
     * @param spaceId    空间 ID
     * @param documentId 文档 ID
     */
    @Override
    @Transactional
    public void deleteDocument(UUID spaceId, UUID documentId) {
        MdDocument document = findActive(spaceId, documentId);
        vectorStoreService.deleteByDocumentId(documentId);
        childChunkMapper.deleteByDocumentId(documentId);
        parentChunkMapper.deleteByDocumentId(documentId);
        assetMapper.deleteByDocumentId(documentId);
        deleteObjectQuietly(document.getObjectKey());
        document.setDeletedAt(Instant.now());
        document.setUpdatedAt(Instant.now());
        documentMapper.update(document);
    }

    /**
     * 监听空间删除事件，级联清理 Markdown 竖井数据。
     *
     * @param event 空间删除事件
     */
    @EventListener
    @Transactional
    public void handleSpaceDeleted(SpaceDeletedEvent event) {
        UUID spaceId = event.spaceId();
        for (MdDocument document : documentMapper.findBySpaceId(spaceId, null, null)) {
            UUID documentId = document.getId();
            vectorStoreService.deleteByDocumentId(documentId);
            deleteObjectQuietly(document.getObjectKey());
        }
        childChunkMapper.deleteBySpaceId(spaceId);
        parentChunkMapper.deleteBySpaceId(spaceId);
        assetMapper.deleteBySpaceId(spaceId);
        documentMapper.softDeleteBySpaceId(spaceId);
    }

    private MdDocument findActive(UUID spaceId, UUID documentId) {
        MdDocument document = documentMapper.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("MdDocument", documentId));
        if (!document.getSpaceId().equals(spaceId)) {
            throw new ResourceNotFoundException("MdDocument", documentId);
        }
        return document;
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidRequestException("Markdown 文件不能为空");
        }
        if (file.getSize() > maxFileSizeMb * 1024 * 1024) {
            throw new InvalidRequestException("Markdown 文件超过最大限制: " + maxFileSizeMb + " MB");
        }
        String mimeType = detectMimeType(file);
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new InvalidRequestException("仅支持 Markdown 文件: " + mimeType);
        }
    }

    private String detectMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        String name = file.getOriginalFilename();
        if (contentType != null && ALLOWED_MIME_TYPES.contains(contentType)) {
            return contentType;
        }
        if (name != null && (name.toLowerCase().endsWith(".md") || name.toLowerCase().endsWith(".markdown"))) {
            return "text/markdown";
        }
        return contentType != null ? contentType : "application/octet-stream";
    }

    private String uploadToObjectStorage(MultipartFile file, MdDocument document) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("kb-md-upload-", ".md");
            file.transferTo(tempFile);
            String objectKey = objectKey(document);
            return objectStorageService.uploadFile(objectKey, tempFile, document.getMimeType());
        } catch (IOException e) {
            throw new IllegalStateException("暂存 Markdown 文件失败", e);
        } finally {
            deleteTempFileQuietly(tempFile);
        }
    }

    private String objectKey(MdDocument document) {
        return "md-documents/%s/%s/source/%s".formatted(
                document.getSpaceId(), document.getId(), sanitizeFilename(document.getOriginalFilename()));
    }

    private void deleteObjectQuietly(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }
        try {
            objectStorageService.deleteFile(objectKey);
        } catch (Exception e) {
            log.warn("删除 Markdown 原始文件对象失败: objectKey={}", objectKey, e);
        }
    }

    private void deleteTempFileQuietly(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            log.warn("删除 Markdown 上传临时文件失败: path={}", tempFile, e);
        }
    }

    private String sanitizeFilename(String name) {
        if (!StringUtils.hasText(name)) {
            return "unknown.md";
        }
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private MdDocumentDto toDto(MdDocument document) {
        return new MdDocumentDto(
                document.getId(),
                document.getSpaceId(),
                document.getTitle(),
                document.getOriginalFilename(),
                document.getFileSizeBytes(),
                document.getMimeType(),
                document.getStatus(),
                document.getChunkCount(),
                document.getErrorMessage(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}
