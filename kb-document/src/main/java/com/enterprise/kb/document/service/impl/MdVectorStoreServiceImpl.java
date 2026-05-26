package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.document.service.MdVectorStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Markdown 专用向量存储服务实现。
 */
@Slf4j
@Service
public class MdVectorStoreServiceImpl implements MdVectorStoreService {

    private static final int BATCH_SIZE = 100;

    private final VectorStore mdVectorStore;

    public MdVectorStoreServiceImpl(@Qualifier("mdVectorStore") VectorStore mdVectorStore) {
        this.mdVectorStore = mdVectorStore;
    }

    /**
     * 批量写入 Markdown child 向量。
     *
     * @param documents 向量文档列表
     */
    @Override
    public void upsert(List<Document> documents) {
        for (int i = 0; i < documents.size(); i += BATCH_SIZE) {
            List<Document> batch = documents.subList(i, Math.min(i + BATCH_SIZE, documents.size()));
            mdVectorStore.add(batch);
        }
        log.debug("写入 Markdown 向量完成，数量={}", documents.size());
    }

    /**
     * 删除指定 Markdown 文档的向量。
     *
     * @param documentId 文档 ID
     */
    @Override
    public void deleteByDocumentId(UUID documentId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        mdVectorStore.delete(b.eq("documentId", documentId.toString()).build());
    }
}
