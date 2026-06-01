package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.common.constants.DocumentStatus;
import com.enterprise.kb.document.mapper.MdChildChunkMapper;
import com.enterprise.kb.document.mapper.MdDocumentAssetMapper;
import com.enterprise.kb.document.mapper.MdDocumentMapper;
import com.enterprise.kb.document.mapper.MdParentChunkMapper;
import com.enterprise.kb.document.markdown.MarkdownStructureIngestionResult;
import com.enterprise.kb.document.model.MdDocument;
import com.enterprise.kb.document.service.DocumentObjectStorageService;
import com.enterprise.kb.document.service.MarkdownStructureIngestionService;
import com.enterprise.kb.document.service.MdDocumentIngestionWorker;
import com.enterprise.kb.document.service.MdVectorStoreService;
import com.enterprise.kb.common.tracing.TracingSupport;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final MdDocumentAssetMapper assetMapper;
    private final MdParentChunkMapper parentChunkMapper;
    private final MdChildChunkMapper childChunkMapper;
    private final MarkdownStructureIngestionService ingestionService;
    private final MdVectorStoreService vectorStoreService;
    private final DocumentObjectStorageService objectStorageService;
    private final TransactionTemplate transactionTemplate;
    private final ObservationRegistry observationRegistry;

    @Value("${enterprise.kb.tracing.enabled:false}")
    private boolean tracingEnabled;

    /**
     * 异步执行 Markdown 结构化入库。
     *
     * @param documentId 文档 ID
     */
    @Override
    @Async("ingestionExecutor")
    public void ingest(UUID documentId) {
        MdDocument document = documentMapper.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Markdown 文档不存在: " + documentId));
        // 入库独立根 trace（ADR-015 C6）：在虚拟线程 worker 内开根 span，不挂在上传 HTTP 请求下
        TracingSupport.root(observationRegistry, tracingEnabled, "kb.ingest.document")
                .documentId(documentId)
                .spaceId(document.getSpaceId())
                .observe(() -> doIngest(document));
    }

    private void doIngest(MdDocument document) {
        UUID documentId = document.getId();
        Path localMarkdown = null;
        try {
            markProcessingAndClearChunks(document);
            vectorStoreService.deleteByDocumentId(documentId);
            localMarkdown = downloadMarkdown(document);
            Path markdownPath = localMarkdown;
            MarkdownStructureIngestionResult result =
                    TracingSupport.span(observationRegistry, tracingEnabled, "kb.ingest.parse")
                            .observe(() -> ingestionService.parse(
                                    document.getId(), document.getSpaceId(), markdownPath.toString()));

            persistChunks(result);
            if (!result.vectorDocuments().isEmpty()) {
                vectorStoreService.upsert(result.vectorDocuments());
            }
            markReady(document, result.children().size());
        } catch (Exception e) {
            log.warn("Markdown 文档入库失败：documentId={}", documentId, e);
            cleanupPartialIngestion(documentId);
            markFailed(document, e);
        } finally {
            deleteTempFileQuietly(localMarkdown);
        }
    }

    private void cleanupPartialIngestion(UUID documentId) {
        try {
            vectorStoreService.deleteByDocumentId(documentId);
        } catch (Exception cleanupError) {
            log.warn("清理 Markdown 向量半成品失败：documentId={}", documentId, cleanupError);
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                childChunkMapper.deleteByDocumentId(documentId);
                parentChunkMapper.deleteByDocumentId(documentId);
                assetMapper.deleteByDocumentId(documentId);
            });
        } catch (Exception cleanupError) {
            log.warn("清理 Markdown 数据库半成品失败：documentId={}", documentId, cleanupError);
        }
    }

    // 上传文档 - 状态更新为处理中 - 支持重复导入或者中途失败场景
    private void markProcessingAndClearChunks(MdDocument document) {
        transactionTemplate.executeWithoutResult(status -> {
            document.setStatus(DocumentStatus.PROCESSING);
            document.setErrorMessage(null);
            document.setUpdatedAt(Instant.now());
            documentMapper.update(document);
            childChunkMapper.deleteByDocumentId(document.getId());
            parentChunkMapper.deleteByDocumentId(document.getId());
            assetMapper.deleteByDocumentId(document.getId());
        });
    }

    private void persistChunks(MarkdownStructureIngestionResult result) {
        transactionTemplate.executeWithoutResult(status -> {
            if (!result.parents().isEmpty()) {
                parentChunkMapper.insertBatch(result.parents());
            }
            if (!result.assets().isEmpty()) {
                assetMapper.insertBatch(result.assets());
            }
            if (!result.children().isEmpty()) {
                childChunkMapper.insertBatch(result.children());
                for (var asset : result.assets()) {
                    if (asset.getChildChunkId() != null) {
                        assetMapper.updateChildChunkId(asset.getId(), asset.getChildChunkId());
                    }
                }
            }
        });
    }

    private void markReady(MdDocument document, int chunkCount) {
        transactionTemplate.executeWithoutResult(status -> {
            document.setStatus(DocumentStatus.READY);
            document.setChunkCount(chunkCount);
            document.setErrorMessage(null);
            document.setUpdatedAt(Instant.now());
            documentMapper.update(document);
        });
    }

    private void markFailed(MdDocument document, Exception e) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                document.setStatus(DocumentStatus.FAILED);
                document.setErrorMessage(e.getMessage());
                document.setUpdatedAt(Instant.now());
                documentMapper.update(document);
            });
        } catch (Exception markFailedError) {
            log.warn("标记 Markdown 文档入库失败状态失败：documentId={}", document.getId(), markFailedError);
        }
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
