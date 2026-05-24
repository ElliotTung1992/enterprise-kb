package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.common.constants.AssetStatus;
import com.enterprise.kb.common.constants.AssetType;
import com.enterprise.kb.common.constants.ChunkContentType;
import com.enterprise.kb.common.constants.DocumentStatus;
import com.enterprise.kb.document.mapper.DocumentAssetMapper;
import com.enterprise.kb.document.mapper.DocumentChunkMapper;
import com.enterprise.kb.document.mapper.DocumentMapper;
import com.enterprise.kb.document.model.DocumentAsset;
import com.enterprise.kb.document.model.DocumentChunk;
import com.enterprise.kb.document.service.VectorStoreService;
import com.enterprise.kb.document.service.VisualAssetWorkerService;
import com.enterprise.kb.document.service.VisualUnderstandingResult;
import com.enterprise.kb.document.service.VisualUnderstandingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 基于数据库轮询的视觉资产异步处理服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisualAssetWorkerServiceImpl implements VisualAssetWorkerService {

    private final DocumentAssetMapper assetMapper;
    private final DocumentChunkMapper chunkMapper;
    private final DocumentMapper documentMapper;
    private final VisualUnderstandingService visualUnderstandingService;
    private final VectorStoreService vectorStoreService;

    @Value("${enterprise.kb.document.visual.worker.batch-size:10}")
    private int batchSize;

    @Value("${enterprise.kb.document.visual.worker.max-retries:3}")
    private int maxRetries;

    @Value("${enterprise.kb.document.visual.worker.retry-delay-seconds:60}")
    private long retryDelaySeconds;

    @Value("${enterprise.kb.ai.default-embedding-provider:MINIMAX}")
    private String defaultEmbeddingProvider;

    /**
     * 定时扫描待处理视觉资产。
     */
    @Override
    @Scheduled(fixedDelayString = "${enterprise.kb.document.visual.worker.fixed-delay-ms:30000}")
    public void processPendingAssets() {
        List<DocumentAsset> assets = assetMapper.findPendingForProcessing(batchSize);
        if (assets.isEmpty()) {
            return;
        }
        log.info("Processing {} pending visual assets", assets.size());
        for (DocumentAsset asset : assets) {
            processAsset(asset);
        }
    }

    /**
     * 处理单个视觉资产，生成视觉语义 chunk 并追加到向量库和 chunk 元数据表。
     *
     * @param asset 视觉资产
     */
    @Override
    public void processAsset(DocumentAsset asset) {
        if (assetMapper.markProcessing(asset.getId()) == 0) {
            return;
        }
        try {
            if (asset.getStatus() == AssetStatus.REINDEX_PENDING) {
                vectorStoreService.deleteByAssetId(asset.getId());
                chunkMapper.deleteVisualByAssetId(asset.getId());
            }
            VisualUnderstandingResult result = visualUnderstandingService.understand(asset);
            asset.setOcrText(result.ocrText());
            asset.setCaption(result.caption());
            asset.setSummary(result.summary());
            asset.setEntities(result.entities());
            asset.setStatus(AssetStatus.READY);
            asset.setLastError(null);
            asset.setUpdatedAt(Instant.now());

            Document visualChunk = buildVisualChunk(asset);
            vectorStoreService.upsert(List.of(visualChunk));
            chunkMapper.insert(toChunkEntity(asset, visualChunk));
            assetMapper.updateUnderstanding(asset);
            recomputeDocumentStatus(asset.getDocumentId());
            log.debug("Processed visual asset assetId={}, documentId={}", asset.getId(), asset.getDocumentId());
        } catch (Exception e) {
            handleFailure(asset, e);
        }
    }

    private Document buildVisualChunk(DocumentAsset asset) {
        ChunkContentType contentType = asset.getAssetType() == AssetType.IMAGE
                ? ChunkContentType.IMAGE_CAPTION
                : ChunkContentType.DIAGRAM_SUMMARY;

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("documentId", asset.getDocumentId().toString());
        metadata.put("contentType", contentType.name());
        metadata.put("assetId", asset.getId().toString());
        metadata.put("section", asset.getSection());
        metadata.put("anchorChunkIndex", asset.getAnchorChunkIndex());
        metadata.put("assetType", asset.getAssetType().name());
        if (asset.getDiagramType() != null) metadata.put("diagramType", asset.getDiagramType().name());

        return new Document(UUID.randomUUID().toString(), buildVisualChunkText(asset, contentType), metadata);
    }

    private String buildVisualChunkText(DocumentAsset asset, ChunkContentType contentType) {
        if (contentType == ChunkContentType.IMAGE_CAPTION) {
            return """
                    [图片说明]
                    图片标题：%s
                    所在章节：%s
                    原始路径：%s
                    替代文本：%s
                    OCR文字：%s
                    图片描述：%s
                    检索摘要：%s
                    关键实体：%s
                    [/图片说明]
                    """.formatted(
                    firstText(asset.getTitle(), asset.getAltText(), asset.getOriginalPath()),
                    firstText(asset.getSection(), "未命名章节"),
                    firstText(asset.getOriginalPath(), ""),
                    firstText(asset.getAltText(), ""),
                    firstText(asset.getOcrText(), ""),
                    firstText(asset.getManualCaption(), asset.getCaption(), ""),
                    firstText(asset.getManualSummary(), asset.getSummary(), ""),
                    firstText(asset.getEntities(), "")).strip();
        }
        return """
                [流程图说明]
                图标题：%s
                图类型：%s
                所在章节：%s
                流程关系：
                OCR文字：%s
                图描述：%s
                检索摘要：%s
                [/流程图说明]
                """.formatted(
                firstText(asset.getTitle(), asset.getSection(), "未命名流程图"),
                asset.getDiagramType() == null ? "" : asset.getDiagramType().name(),
                firstText(asset.getSection(), "未命名章节"),
                firstText(asset.getOcrText(), ""),
                firstText(asset.getManualCaption(), asset.getCaption(), ""),
                firstText(asset.getManualSummary(), asset.getSummary(), "")).strip();
    }

    private DocumentChunk toChunkEntity(DocumentAsset asset, Document visualChunk) {
        DocumentChunk entity = new DocumentChunk();
        entity.setId(UUID.fromString(visualChunk.getId()));
        entity.setDocumentId(asset.getDocumentId());
        entity.setContentType(asset.getAssetType() == AssetType.IMAGE
                ? ChunkContentType.IMAGE_CAPTION
                : ChunkContentType.DIAGRAM_SUMMARY);
        entity.setAssetId(asset.getId());
        entity.setChunkIndex(chunkMapper.findMaxChunkIndexByDocumentId(asset.getDocumentId()) + 1);
        entity.setContent(visualChunk.getText());
        entity.setMilvusId(visualChunk.getId());
        entity.setEmbeddingModel(defaultEmbeddingProvider.toLowerCase());
        entity.setSection(asset.getSection());
        entity.setAnchorChunkIndex(asset.getAnchorChunkIndex());
        return entity;
    }

    private void handleFailure(DocumentAsset asset, Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (asset.getRetryCount() != null && asset.getRetryCount() + 1 < maxRetries) {
            Instant nextRetryAt = Instant.now().plusSeconds(retryDelaySeconds);
            assetMapper.markRetry(asset.getId(), nextRetryAt, message);
            log.warn("Visual asset processing failed, scheduled retry assetId={}, nextRetryAt={}",
                    asset.getId(), nextRetryAt, e);
        } else {
            assetMapper.markFailed(asset.getId(), message);
            recomputeDocumentStatus(asset.getDocumentId());
            log.warn("Visual asset processing failed permanently assetId={}", asset.getId(), e);
        }
    }

    private void recomputeDocumentStatus(UUID documentId) {
        long pending = assetMapper.countByDocumentIdAndStatuses(documentId,
                List.of(AssetStatus.PENDING.name(), AssetStatus.PROCESSING.name(),
                        AssetStatus.REINDEX_PENDING.name(), AssetStatus.REINDEXING.name()));
        long failed = assetMapper.countByDocumentIdAndStatuses(documentId, List.of(AssetStatus.FAILED.name()));
        documentMapper.findByIdAndDeletedAtIsNull(documentId).ifPresent(document -> {
            if (pending > 0) {
                document.setStatus(DocumentStatus.READY_WITH_PENDING_ASSETS);
            } else if (failed > 0) {
                document.setStatus(DocumentStatus.READY_WITH_ASSET_ERRORS);
            } else {
                document.setStatus(DocumentStatus.READY);
            }
            document.setChunkCount((int) chunkMapper.countByDocumentId(documentId));
            document.setUpdatedAt(Instant.now());
            documentMapper.update(document);
        });
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }
}
