package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.document.service.ChunkingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 文档分块服务实现，基于 TokenTextSplitter 对文档进行拆分。
 * <p>将长文档按 token 数量拆分为重叠的块，并在每个 chunk 的元数据中注入
 * documentId、spaceId 和 chunkIndex，便于后续检索和溯源。</p>
 */
@Slf4j
@Service
public class ChunkingServiceImpl implements ChunkingService {

    @Value("${enterprise.kb.chunking.chunk-size:512}")
    private int chunkSize;

    @Value("${enterprise.kb.chunking.overlap-size:64}")
    private int overlapSize;

    /**
     * 将文档列表分块。
     * <p>使用 TokenTextSplitter 按 token 数量拆分，重叠度由 overlapSize 控制。</p>
     *
     * @param documents  源文档列表
     * @param documentId 父文档 UUID
     * @param spaceId    空间 UUID
     * @return 分块后的文档列表
     */
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
