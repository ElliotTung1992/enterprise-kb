package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.document.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 向量存储服务实现，负责将文档块 upsert 到 Milvus 并支持按 documentId 删除。
 * <p>批量写入以 100 条为单元，避免单次请求过大。删除使用 FilterExpression 匹配 documentId。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreServiceImpl implements VectorStoreService {

    private final VectorStore vectorStore;
    private static final int BATCH_SIZE = 100;

    /**
     * 批量 upsert 文档块到向量库。
     *
     * @param documents 文档块列表
     */
    @Override
    public void upsert(List<Document> documents) {
        for (int i = 0; i < documents.size(); i += BATCH_SIZE) {
            List<Document> batch = documents.subList(i, Math.min(i + BATCH_SIZE, documents.size()));
            vectorStore.add(batch);
            log.debug("Upserted batch {}/{}", i / BATCH_SIZE + 1,
                    (documents.size() + BATCH_SIZE - 1) / BATCH_SIZE);
        }
    }

    /**
     * 删除文档关联的所有向量。
     *
     * @param documentId 文档 UUID
     */
    @Override
    public void deleteByDocumentId(UUID documentId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        vectorStore.delete(b.eq("documentId", documentId.toString()).build());
        log.debug("Deleted vectors for documentId={}", documentId);
    }
}
