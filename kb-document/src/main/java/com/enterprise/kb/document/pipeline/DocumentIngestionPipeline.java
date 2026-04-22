package com.enterprise.kb.document.pipeline;

import com.enterprise.kb.common.constants.DocumentStatus;
import com.enterprise.kb.document.mapper.DocumentMapper;
import com.enterprise.kb.document.model.Document;
import com.enterprise.kb.document.service.ChunkMetadataService;
import com.enterprise.kb.document.service.ChunkingService;
import com.enterprise.kb.document.service.DocumentParserService;
import com.enterprise.kb.document.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentIngestionPipeline {

    private final DocumentMapper documentMapper;
    private final DocumentParserService parserService;
    private final ChunkingService chunkingService;
    private final VectorStoreService vectorStoreService;
    private final ChunkMetadataService chunkMetadataService;

    @Value("${enterprise.kb.ai.default-embedding-provider:MINIMAX}")
    private String defaultEmbeddingProvider;

    @Async("ingestionExecutor")
    @Transactional
    public void ingest(IngestionContext ctx) {
        log.info("Starting ingestion for documentId={}", ctx.getDocumentId());

        Document doc = documentMapper.findByIdAndDeletedAtIsNull(ctx.getDocumentId())
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + ctx.getDocumentId()));

        try {
            // Stage 1: PARSING
            doc.setStatus(DocumentStatus.PROCESSING);
            doc.setUpdatedAt(Instant.now());
            documentMapper.update(doc);
            List<org.springframework.ai.document.Document> parsed =
                    parserService.parse(ctx.getFilePath(), ctx.getMimeType());
            log.info("Parsed {} raw document sections for documentId={}", parsed.size(), ctx.getDocumentId());

            // Stage 2: CHUNKING
            List<org.springframework.ai.document.Document> chunks =
                    chunkingService.chunk(parsed, ctx.getDocumentId(), ctx.getSpaceId());
            log.info("Created {} chunks for documentId={}", chunks.size(), ctx.getDocumentId());

            // Stage 3 & 4: EMBEDDING + VECTOR STORE
            // VectorStore.add() internally calls EmbeddingModel and stores vectors
            vectorStoreService.upsert(chunks);

            // Stage 5: PERSIST CHUNK METADATA
            String embeddingModel = defaultEmbeddingProvider.toLowerCase();
            chunkMetadataService.saveChunks(ctx.getDocumentId(), chunks, embeddingModel);

            // Mark READY
            doc.setChunkCount(chunks.size());
            doc.setStatus(DocumentStatus.READY);
            doc.setErrorMessage(null);
            doc.setUpdatedAt(Instant.now());
            documentMapper.update(doc);
            log.info("Ingestion complete for documentId={}, chunks={}", ctx.getDocumentId(), chunks.size());

        } catch (Exception e) {
            log.error("Ingestion failed for documentId={}", ctx.getDocumentId(), e);
            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage(e.getMessage());
            doc.setUpdatedAt(Instant.now());
            documentMapper.update(doc);
        }
    }
}
