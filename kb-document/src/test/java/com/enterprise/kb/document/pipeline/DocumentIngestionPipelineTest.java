package com.enterprise.kb.document.pipeline;

import com.enterprise.kb.common.constants.DocumentStatus;
import com.enterprise.kb.document.mapper.DocumentMapper;
import com.enterprise.kb.document.model.Document;
import com.enterprise.kb.document.service.ChunkMetadataService;
import com.enterprise.kb.document.service.ChunkingService;
import com.enterprise.kb.document.service.DocumentParserService;
import com.enterprise.kb.document.service.MarkdownVisualIngestionService;
import com.enterprise.kb.document.service.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionPipelineTest {

    @Mock DocumentMapper documentMapper;
    @Mock DocumentParserService parserService;
    @Mock ChunkingService chunkingService;
    @Mock VectorStoreService vectorStoreService;
    @Mock ChunkMetadataService chunkMetadataService;
    @Mock MarkdownVisualIngestionService markdownVisualIngestionService;

    private DocumentIngestionPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new DocumentIngestionPipeline(
                documentMapper,
                parserService,
                chunkingService,
                vectorStoreService,
                chunkMetadataService,
                markdownVisualIngestionService);
        ReflectionTestUtils.setField(pipeline, "defaultEmbeddingProvider", "DASHSCOPE");
    }

    @Test
    void ingestClearsOldChunksVectorsAndVisualAssetsBeforeRebuilding() {
        UUID documentId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        IngestionContext ctx = IngestionContext.builder()
                .documentId(documentId)
                .spaceId(spaceId)
                .filePath("/tmp/demo.md")
                .mimeType("text/markdown")
                .build();
        Document document = new Document();
        document.setId(documentId);
        document.setSpaceId(spaceId);
        document.setStatus(DocumentStatus.PENDING);
        when(documentMapper.findByIdAndDeletedAtIsNull(documentId)).thenReturn(Optional.of(document));
        when(markdownVisualIngestionService.supports(ctx)).thenReturn(false);
        List<org.springframework.ai.document.Document> parsed = List.of(
                new org.springframework.ai.document.Document("正文", Map.of("section", "首页")));
        List<org.springframework.ai.document.Document> chunks = List.of(
                new org.springframework.ai.document.Document("chunk", Map.of("section", "首页")));
        when(parserService.parse(ctx.getFilePath(), ctx.getMimeType())).thenReturn(parsed);
        when(chunkingService.chunk(parsed, documentId, spaceId)).thenReturn(chunks);

        pipeline.ingest(ctx);

        InOrder order = inOrder(vectorStoreService, chunkMetadataService, markdownVisualIngestionService);
        order.verify(vectorStoreService).deleteByDocumentId(documentId);
        order.verify(chunkMetadataService).deleteByDocumentId(documentId);
        order.verify(markdownVisualIngestionService).deleteByDocumentId(documentId);
        order.verify(vectorStoreService).upsert(chunks);
        order.verify(chunkMetadataService).saveChunks(eq(documentId), anyList(), eq("dashscope"));
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(document.getChunkCount()).isEqualTo(1);
    }
}
