package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.document.service.ChunkingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ChunkingServiceImpl implements ChunkingService {

    @Value("${enterprise.kb.chunking.chunk-size:512}")
    private int chunkSize;

    @Value("${enterprise.kb.chunking.overlap-size:64}")
    private int overlapSize;

    @Override
    public List<Document> chunk(List<Document> documents, UUID documentId, UUID spaceId) {
        TokenTextSplitter splitter = new TokenTextSplitter(chunkSize, overlapSize, 5, 10000, true);
        List<Document> chunks = splitter.apply(documents);
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            chunk.getMetadata().put("documentId", documentId.toString());
            chunk.getMetadata().put("spaceId", spaceId.toString());
            chunk.getMetadata().put("chunkIndex", i);
        }
        log.debug("Split {} documents into {} chunks for documentId={}", documents.size(), chunks.size(), documentId);
        return chunks;
    }
}
