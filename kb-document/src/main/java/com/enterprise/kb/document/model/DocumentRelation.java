package com.enterprise.kb.document.model;

import com.enterprise.kb.common.constants.RelationType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class DocumentRelation {

    private UUID id;
    private UUID sourceDocId;
    private UUID targetDocId;
    private RelationType relationType;
    private BigDecimal weight = BigDecimal.ONE;
    private boolean autoDetected = false;
    private UUID createdBy;
    private Instant createdAt = Instant.now();
}
