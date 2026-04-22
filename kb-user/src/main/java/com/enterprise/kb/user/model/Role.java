package com.enterprise.kb.user.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class Role {

    private UUID id;
    private String name;
    private String description;
    private Instant createdAt = Instant.now();
}
