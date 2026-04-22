package com.enterprise.kb.document.model;

import com.enterprise.kb.common.constants.DocumentStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class Document {

    private UUID id;
    private UUID spaceId;
    private UUID uploadedBy;
    private String title;
    private String originalFilename;
    private String filePath;
    private Long fileSizeBytes;
    private String mimeType;
    private String sourceUrl;
    private DocumentStatus status = DocumentStatus.PENDING;
    private String errorMessage;
    private Integer chunkCount = 0;
    private String language;
    private Map<String, Object> metadata;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private Instant deletedAt;
}
