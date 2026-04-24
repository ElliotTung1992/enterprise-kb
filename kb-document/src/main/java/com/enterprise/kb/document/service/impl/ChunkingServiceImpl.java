package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.document.service.ChunkingService;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * 文档分块服务实现，基于 jtokkit（CL100K_BASE）对文档进行 token 级滑动窗口分块。
 * <p>相邻 chunk 之间保留 overlapTokens 个 token 的重叠，避免语义在边界处被割裂。</p>
 */
@Slf4j
@Service
public class ChunkingServiceImpl implements ChunkingService {

    @Value("${enterprise.kb.chunking.chunk-size:512}")
    private int chunkSize;

    /**
     * 相邻 chunk 之间重叠的 token 数。
     * 重叠确保被 chunk 边界截断的语义单元在两个相邻 chunk 中均可被检索到。
     * 建议设为 chunk-size 的 10%~20%，默认 100（约 512 的 20%）。
     */
    @Value("${enterprise.kb.chunking.overlap-tokens:100}")
    private int overlapTokens;

    /** 长度 <= 此值的 chunk 视为无意义残片，直接丢弃。 */
    private static final int MIN_CHUNK_LENGTH = 5;

    private final Encoding encoding;

    public ChunkingServiceImpl() {
        this.encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
    }

    /**
     * 将文档列表分块，并在每个 chunk 的元数据中注入 documentId、spaceId 和 chunkIndex。
     *
     * @param documents  源文档列表
     * @param documentId 父文档 UUID
     * @param spaceId    空间 UUID
     * @return 分块后的文档列表
     */
    @Override
    public List<Document> chunk(List<Document> documents, UUID documentId, UUID spaceId) {
        List<Document> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (Document doc : documents) {
            IntArrayList tokens = encoding.encode(doc.getText());
            int start = 0;
            while (start < tokens.size()) {
                int end = Math.min(start + chunkSize, tokens.size());

                IntArrayList window = new IntArrayList(end - start);
                for (int i = start; i < end; i++) {
                    window.add(tokens.get(i));
                }

                String chunkText = encoding.decode(window).trim();
                if (chunkText.length() > MIN_CHUNK_LENGTH) {
                    // 显式指定 UUID，使 Milvus ID 与 PostgreSQL document_chunks.id 保持一致，避免混合搜索重复
                    String chunkId = UUID.randomUUID().toString();
                    Document chunk = new Document(chunkId, chunkText, new HashMap<>(doc.getMetadata()));
                    chunk.getMetadata().put("documentId", documentId.toString());
                    chunk.getMetadata().put("spaceId", spaceId.toString());
                    chunk.getMetadata().put("chunkIndex", chunkIndex++);
                    chunks.add(chunk);
                }

                if (end >= tokens.size()) break;
                start += (chunkSize - overlapTokens);
            }
        }

        log.debug("Split {} documents into {} chunks (chunkSize={}, overlapTokens={}) for documentId={}",
                documents.size(), chunks.size(), chunkSize, overlapTokens, documentId);
        return chunks;
    }
}
