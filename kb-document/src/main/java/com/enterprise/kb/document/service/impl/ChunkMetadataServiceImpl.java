package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.document.service.ChunkMetadataService;
import com.enterprise.kb.document.mapper.DocumentChunkMapper;
import com.enterprise.kb.document.model.DocumentChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkMetadataServiceImpl implements ChunkMetadataService {

    private final DocumentChunkMapper chunkMapper;

    @Override
    @Transactional
    public void saveChunks(UUID documentId, List<Document> chunks, String embeddingModel) {
        chunkMapper.deleteByDocumentId(documentId);
        List<DocumentChunk> entities = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            DocumentChunk entity = new DocumentChunk();
            entity.setId(UUID.randomUUID());
            entity.setDocumentId(documentId);
            entity.setChunkIndex(i);
            entity.setContent(chunk.getText());
            entity.setEmbeddingModel(embeddingModel);
            Object milvusId = chunk.getMetadata().get("id");
            if (milvusId != null) entity.setMilvusId(milvusId.toString());
            Object pageNum = chunk.getMetadata().get("page_number");
            if (pageNum != null) entity.setPageNumber(Integer.parseInt(pageNum.toString()));
            entities.add(entity);
        }
        if (!entities.isEmpty()) chunkMapper.insertBatch(entities);
        log.debug("Saved {} chunks for documentId={}", entities.size(), documentId);
    }

    @Override
    @Transactional
    public void deleteByDocumentId(UUID documentId) {
        chunkMapper.deleteByDocumentId(documentId);
    }
}
