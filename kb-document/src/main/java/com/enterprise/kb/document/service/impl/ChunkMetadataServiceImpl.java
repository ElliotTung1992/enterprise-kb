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

/**
 * Chunk 元数据持久化服务实现，负责将文档分块信息存入数据库。
 * <p>每次保存时会先清除该文档的旧 chunk 记录，再批量插入新 chunk。
 * 记录包含内容、嵌入模型、Milvus ID 和页码等辅助信息。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkMetadataServiceImpl implements ChunkMetadataService {

    private final DocumentChunkMapper chunkMapper;

    /**
     * 保存文档 chunks。
     * <p>先删除旧 chunk 记录，再批量插入新 chunk。</p>
     *
     * @param documentId    文档 UUID
     * @param chunks        chunk 列表
     * @param embeddingModel 嵌入模型名称
     */
    @Override
    @Transactional
    public void saveChunks(UUID documentId, List<Document> chunks, String embeddingModel) {
        chunkMapper.deleteByDocumentId(documentId);
        List<DocumentChunk> entities = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            DocumentChunk entity = new DocumentChunk();
            // 复用 Milvus Document ID，与 PostgreSQL 主键保持一致，确保混合搜索能正确去重
            entity.setId(UUID.fromString(chunk.getId()));
            entity.setMilvusId(chunk.getId());
            entity.setDocumentId(documentId);
            entity.setChunkIndex(i);
            entity.setContent(chunk.getText());
            entity.setEmbeddingModel(embeddingModel);
            Object pageNum = chunk.getMetadata().get("page_number");
            if (pageNum != null) entity.setPageNumber(Integer.parseInt(pageNum.toString()));
            entities.add(entity);
        }
        if (!entities.isEmpty()) chunkMapper.insertBatch(entities);
        log.debug("Saved {} chunks for documentId={}", entities.size(), documentId);
    }

    /**
     * 删除文档的所有 chunk 记录。
     *
     * @param documentId 文档 UUID
     */
    @Override
    @Transactional
    public void deleteByDocumentId(UUID documentId) {
        chunkMapper.deleteByDocumentId(documentId);
    }
}
