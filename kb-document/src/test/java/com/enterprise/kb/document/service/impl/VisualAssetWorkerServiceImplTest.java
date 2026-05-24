package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.common.constants.AssetStatus;
import com.enterprise.kb.common.constants.AssetType;
import com.enterprise.kb.common.constants.ChunkContentType;
import com.enterprise.kb.common.constants.DocumentStatus;
import com.enterprise.kb.document.mapper.DocumentAssetMapper;
import com.enterprise.kb.document.mapper.DocumentChunkMapper;
import com.enterprise.kb.document.mapper.DocumentMapper;
import com.enterprise.kb.document.model.Document;
import com.enterprise.kb.document.model.DocumentAsset;
import com.enterprise.kb.document.model.DocumentChunk;
import com.enterprise.kb.document.service.VectorStoreService;
import com.enterprise.kb.document.service.VisualUnderstandingResult;
import com.enterprise.kb.document.service.VisualUnderstandingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisualAssetWorkerServiceImplTest {

    @Mock DocumentAssetMapper assetMapper;
    @Mock DocumentChunkMapper chunkMapper;
    @Mock DocumentMapper documentMapper;
    @Mock VisualUnderstandingService visualUnderstandingService;
    @Mock VectorStoreService vectorStoreService;

    private VisualAssetWorkerServiceImpl worker;

    @BeforeEach
    void setUp() {
        worker = new VisualAssetWorkerServiceImpl(
                assetMapper, chunkMapper, documentMapper, visualUnderstandingService, vectorStoreService);
        ReflectionTestUtils.setField(worker, "batchSize", 10);
        ReflectionTestUtils.setField(worker, "maxRetries", 3);
        ReflectionTestUtils.setField(worker, "retryDelaySeconds", 60L);
        ReflectionTestUtils.setField(worker, "defaultEmbeddingProvider", "DASHSCOPE");
    }

    @Test
    void processAssetWritesCaptionChunkAndMarksDocumentReady() {
        DocumentAsset asset = imageAsset(AssetStatus.PENDING, 0);
        Document document = activeDocument(asset.getDocumentId());
        when(assetMapper.markProcessing(asset.getId())).thenReturn(1);
        when(visualUnderstandingService.understand(asset))
                .thenReturn(new VisualUnderstandingResult("OCR", "模型描述", "检索摘要", "关键实体"));
        when(chunkMapper.findMaxChunkIndexByDocumentId(asset.getDocumentId())).thenReturn(4);
        when(assetMapper.countByDocumentIdAndStatuses(eq(asset.getDocumentId()), anyList())).thenReturn(0L);
        when(documentMapper.findByIdAndDeletedAtIsNull(asset.getDocumentId())).thenReturn(Optional.of(document));
        when(chunkMapper.countByDocumentId(asset.getDocumentId())).thenReturn(6L);

        worker.processAsset(asset);

        ArgumentCaptor<DocumentChunk> chunkCaptor = ArgumentCaptor.forClass(DocumentChunk.class);
        verify(chunkMapper).insert(chunkCaptor.capture());
        assertThat(chunkCaptor.getValue().getContentType()).isEqualTo(ChunkContentType.IMAGE_CAPTION);
        assertThat(chunkCaptor.getValue().getChunkIndex()).isEqualTo(5);
        assertThat(chunkCaptor.getValue().getContent()).contains("OCR", "模型描述", "检索摘要", "关键实体");
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.READY);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(document.getChunkCount()).isEqualTo(6);
        verify(vectorStoreService).upsert(anyList());
        verify(assetMapper).updateUnderstanding(asset);
        verify(documentMapper).update(document);
    }

    @Test
    void failedAssetSchedulesRetryBeforeRetryLimit() {
        DocumentAsset asset = imageAsset(AssetStatus.PENDING, 0);
        when(assetMapper.markProcessing(asset.getId())).thenReturn(1);
        when(visualUnderstandingService.understand(asset)).thenThrow(new IllegalStateException("provider timeout"));

        worker.processAsset(asset);

        verify(assetMapper).markRetry(eq(asset.getId()), any(), eq("provider timeout"));
        verify(assetMapper, never()).markFailed(any(), any());
        verify(vectorStoreService, never()).upsert(anyList());
    }

    @Test
    void failedAssetMarksFailedAfterRetryLimit() {
        DocumentAsset asset = imageAsset(AssetStatus.PENDING, 2);
        Document document = activeDocument(asset.getDocumentId());
        when(assetMapper.markProcessing(asset.getId())).thenReturn(1);
        when(visualUnderstandingService.understand(asset)).thenThrow(new IllegalStateException("bad image"));
        when(assetMapper.countByDocumentIdAndStatuses(eq(asset.getDocumentId()), anyList()))
                .thenReturn(0L, 1L);
        when(documentMapper.findByIdAndDeletedAtIsNull(asset.getDocumentId())).thenReturn(Optional.of(document));
        when(chunkMapper.countByDocumentId(asset.getDocumentId())).thenReturn(3L);

        worker.processAsset(asset);

        verify(assetMapper).markFailed(asset.getId(), "bad image");
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY_WITH_ASSET_ERRORS);
    }

    @Test
    void reindexDeletesOldVisualChunkAndVectorBeforeRebuild() {
        DocumentAsset asset = imageAsset(AssetStatus.REINDEX_PENDING, 0);
        asset.setManualCaption("人工描述");
        asset.setManualSummary("人工摘要");
        Document document = activeDocument(asset.getDocumentId());
        when(assetMapper.markProcessing(asset.getId())).thenReturn(1);
        when(visualUnderstandingService.understand(asset))
                .thenReturn(new VisualUnderstandingResult("", "模型描述", "模型摘要", ""));
        when(chunkMapper.findMaxChunkIndexByDocumentId(asset.getDocumentId())).thenReturn(7);
        when(assetMapper.countByDocumentIdAndStatuses(eq(asset.getDocumentId()), anyList())).thenReturn(0L);
        when(documentMapper.findByIdAndDeletedAtIsNull(asset.getDocumentId())).thenReturn(Optional.of(document));
        when(chunkMapper.countByDocumentId(asset.getDocumentId())).thenReturn(8L);

        worker.processAsset(asset);

        ArgumentCaptor<DocumentChunk> chunkCaptor = ArgumentCaptor.forClass(DocumentChunk.class);
        verify(vectorStoreService).deleteByAssetId(asset.getId());
        verify(chunkMapper).deleteVisualByAssetId(asset.getId());
        verify(chunkMapper).insert(chunkCaptor.capture());
        assertThat(chunkCaptor.getValue().getContent()).contains("人工描述", "人工摘要");
    }

    private DocumentAsset imageAsset(AssetStatus status, int retryCount) {
        DocumentAsset asset = new DocumentAsset();
        asset.setId(UUID.randomUUID());
        asset.setDocumentId(UUID.randomUUID());
        asset.setAssetType(AssetType.IMAGE);
        asset.setStatus(status);
        asset.setRetryCount(retryCount);
        asset.setTitle("架构图");
        asset.setOriginalPath("images/architecture.svg");
        asset.setAltText("架构图");
        asset.setSection("图片章节");
        asset.setAnchorChunkIndex(2);
        return asset;
    }

    private Document activeDocument(UUID documentId) {
        Document document = new Document();
        document.setId(documentId);
        document.setStatus(DocumentStatus.READY_WITH_PENDING_ASSETS);
        return document;
    }
}
