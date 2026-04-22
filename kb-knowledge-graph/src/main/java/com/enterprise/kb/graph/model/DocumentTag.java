package com.enterprise.kb.graph.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class DocumentTag {

    private UUID id;
    private UUID documentId;
    private UUID tagId;
    private UUID taggedBy;
    private boolean autoTagged = false;
    private BigDecimal confidence;
    private Instant createdAt = Instant.now();
}
