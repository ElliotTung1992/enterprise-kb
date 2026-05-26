package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.common.constants.DocumentStatus;
import com.enterprise.kb.document.mapper.MdChildChunkMapper;
import com.enterprise.kb.document.mapper.MdDocumentMapper;
import com.enterprise.kb.document.mapper.MdParentChunkMapper;
import com.enterprise.kb.document.markdown.MarkdownStructureIngestionResult;
import com.enterprise.kb.document.model.MdDocument;
import com.enterprise.kb.document.service.DocumentObjectStorageService;
import com.enterprise.kb.document.service.MarkdownStructureIngestionService;
import com.enterprise.kb.document.service.MdDocumentIngestionWorker;
import com.enterprise.kb.document.service.MdVectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * Markdown 文档异步入库 Worker 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MdDocumentIngestionWorkerImpl implements MdDocumentIngestionWorker {

    private final MdDocumentMapper documentMapper;
    private final MdParentChunkMapper parentChunkMapper;
    private final MdChildChunkMapper childChunkMapper;
    private final MarkdownStructureIngestionService ingestionService;
    private final MdVectorStoreService vectorStoreService;
    private final DocumentObjectStorageService objectStorageService;

    /**
     * 异步执行 Markdown 结构化入库。
     *
     * @param documentId 文档 ID
     */
    @Override
    @Async("ingestionExecutor")
    @Transactional
    public void ingest(UUID documentId) {
        MdDocument document = documentMapper.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Markdown 文档不存在: " + documentId));
        Path localMarkdown = null;
        try {
            markProcessing(document);
            vectorStoreService.deleteByDocumentId(documentId);
            childChunkMapper.deleteByDocumentId(documentId);
            parentChunkMapper.deleteByDocumentId(documentId);

            // 下载文件
            localMarkdown = downloadMarkdown(document);
            // 加载解析文件
            MarkdownStructureIngestionResult result = ingestionService.parse(
                    document.getId(), document.getSpaceId(), localMarkdown.toString());
            if (!result.parents().isEmpty()) {
                parentChunkMapper.insertBatch(result.parents());
            }
            if (!result.children().isEmpty()) {
                childChunkMapper.insertBatch(result.children());
                vectorStoreService.upsert(result.vectorDocuments());
            }
            document.setStatus(DocumentStatus.READY);
            document.setChunkCount(result.children().size());
            document.setErrorMessage(null);
            document.setUpdatedAt(Instant.now());
            documentMapper.update(document);
        } catch (Exception e) {
            log.warn("Markdown 文档入库失败：documentId={}", documentId, e);
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage(e.getMessage());
            document.setUpdatedAt(Instant.now());
            documentMapper.update(document);
        } finally {
            deleteTempFileQuietly(localMarkdown);
        }
    }

    private void markProcessing(MdDocument document) {
        document.setStatus(DocumentStatus.PROCESSING);
        document.setUpdatedAt(Instant.now());
        documentMapper.update(document);
    }

    private Path downloadMarkdown(MdDocument document) throws IOException {
        Path tempFile = Files.createTempFile("kb-md-ingest-", ".md");
        try {
            objectStorageService.downloadFile(document.getObjectKey(), tempFile);
            return tempFile;
        } catch (RuntimeException e) {
            deleteTempFileQuietly(tempFile);
            throw e;
        }
    }

    private void deleteTempFileQuietly(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            log.warn("删除 Markdown 入库临时文件失败: path={}", tempFile, e);
        }
    }
}
