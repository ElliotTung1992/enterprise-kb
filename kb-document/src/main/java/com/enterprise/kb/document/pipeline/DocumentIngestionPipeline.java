package com.enterprise.kb.document.pipeline;

import com.enterprise.kb.common.constants.DocumentStatus;
import com.enterprise.kb.document.mapper.DocumentMapper;
import com.enterprise.kb.document.model.Document;
import com.enterprise.kb.document.service.ChunkMetadataService;
import com.enterprise.kb.document.service.ChunkingService;
import com.enterprise.kb.document.service.DocumentParserService;
import com.enterprise.kb.document.service.MarkdownVisualIngestionResult;
import com.enterprise.kb.document.service.MarkdownVisualIngestionService;
import com.enterprise.kb.document.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentIngestionPipeline {

    private final DocumentMapper documentMapper;
    private final DocumentParserService parserService;
    private final ChunkingService chunkingService;
    private final VectorStoreService vectorStoreService;
    private final ChunkMetadataService chunkMetadataService;
    private final MarkdownVisualIngestionService markdownVisualIngestionService;

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
            markdownVisualIngestionService.deleteByDocumentId(ctx.getDocumentId());
            MarkdownVisualIngestionResult visualResult = MarkdownVisualIngestionResult.empty();
            List<org.springframework.ai.document.Document> parsed;
            if (markdownVisualIngestionService.supports(ctx)) {
                visualResult = markdownVisualIngestionService.extract(ctx);
                parsed = visualResult.textDocuments();
                Map<String, Object> metadata = doc.getMetadata() == null ? new HashMap<>() : new HashMap<>(doc.getMetadata());
                metadata.put("source_zip_object_key", visualResult.sourceZipObjectKey());
                metadata.put("main_markdown_path", visualResult.mainMarkdownPath());
                doc.setMetadata(metadata);
            } else {
                parsed = parserService.parse(ctx.getFilePath(), ctx.getMimeType());
            }
            log.info("Parsed {} raw document sections for documentId={}", parsed.size(), ctx.getDocumentId());

            // Stage 2: CHUNKING
            List<org.springframework.ai.document.Document> chunks =
                    chunkingService.chunk(parsed, ctx.getDocumentId(), ctx.getSpaceId());
            List<org.springframework.ai.document.Document> allChunks = new ArrayList<>(chunks);
            if (!visualResult.assets().isEmpty()) {
                List<org.springframework.ai.document.Document> visualChunks =
                        markdownVisualIngestionService.buildReferenceChunks(ctx, visualResult.assets(), sectionAnchors(chunks));
                allChunks.addAll(visualChunks);
                markdownVisualIngestionService.saveAssets(visualResult.assets());
            }
            log.info("Created {} chunks for documentId={}", allChunks.size(), ctx.getDocumentId());

            // Stage 3 & 4: EMBEDDING + VECTOR STORE
            // VectorStore.add() internally calls EmbeddingModel and stores vectors
            vectorStoreService.upsert(allChunks);

            // Stage 5: PERSIST CHUNK METADATA
            String embeddingModel = defaultEmbeddingProvider.toLowerCase();
            chunkMetadataService.saveChunks(ctx.getDocumentId(), allChunks, embeddingModel);

            // Mark READY
            doc.setChunkCount(allChunks.size());
            doc.setStatus(DocumentStatus.READY);
            doc.setErrorMessage(null);
            doc.setUpdatedAt(Instant.now());
            documentMapper.update(doc);
            log.info("Ingestion complete for documentId={}, chunks={}", ctx.getDocumentId(), allChunks.size());

        } catch (Exception e) {
            log.error("Ingestion failed for documentId={}", ctx.getDocumentId(), e);
            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage(e.getMessage());
            doc.setUpdatedAt(Instant.now());
            documentMapper.update(doc);
        }
    }

    private Map<String, Integer> sectionAnchors(List<org.springframework.ai.document.Document> chunks) {
        Map<String, Integer> anchors = new HashMap<>();
        for (int i = 0; i < chunks.size(); i++) {
            Object section = chunks.get(i).getMetadata().get("section");
            String key = section == null ? "" : section.toString();
            anchors.putIfAbsent(key, i);
        }
        return anchors;
    }
}
