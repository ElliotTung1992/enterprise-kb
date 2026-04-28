package com.enterprise.kb.search.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class QaChatMessage {

    private UUID id;
    private UUID sessionId;
    private String role;
    private String content;
    private Instant createdAt;
}
