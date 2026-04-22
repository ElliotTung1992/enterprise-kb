package com.enterprise.kb.document.service;

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

    @Override
    @Transactional(readOnly = true)
    public DocumentDto getDocument(UUID docId) {
        return toDto(findActive(docId));
    }

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
