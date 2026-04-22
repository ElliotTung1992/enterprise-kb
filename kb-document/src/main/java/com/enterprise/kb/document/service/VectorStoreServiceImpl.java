package com.enterprise.kb.document.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreServiceImpl implements VectorStoreService {

    private final VectorStore vectorStore;
    private static final int BATCH_SIZE = 100;

    @Override
    public void upsert(List<Document> documents) {
        for (int i = 0; i < documents.size(); i += BATCH_SIZE) {
            List<Document> batch = documents.subList(i, Math.min(i + BATCH_SIZE, documents.size()));
            vectorStore.add(batch);
            log.debug("Upserted batch {}/{}", i / BATCH_SIZE + 1,
                    (documents.size() + BATCH_SIZE - 1) / BATCH_SIZE);
        }
    }

    @Override
    public void deleteByDocumentId(UUID documentId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        vectorStore.delete(b.eq("documentId", documentId.toString()).build());
        log.debug("Deleted vectors for documentId={}", documentId);
    }
}
