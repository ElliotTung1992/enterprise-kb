package com.enterprise.kb.document.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class DocumentChunk {

    private UUID id;
    private UUID documentId;
    private Integer chunkIndex;
    private String content;
    private Integer tokenCount;
    private String milvusId;
    private String embeddingModel;
    private Integer pageNumber;
    private Integer charStart;
    private Integer charEnd;
    private Instant createdAt = Instant.now();
}
