package com.enterprise.kb.document.pipeline;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class IngestionContext {
    private final UUID documentId;
    private final UUID spaceId;
    private final String filePath;
    private final String mimeType;
    private final String embeddingProvider;
}
