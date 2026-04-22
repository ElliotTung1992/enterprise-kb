package com.enterprise.kb.graph.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class Tag {

    private UUID id;
    private UUID spaceId;
    private String name;
    private String slug;
    private String color;
    private UUID parentId;
    private String tagType = "TAG";
    private String description;
    private Integer sortOrder = 0;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private Instant deletedAt;
}
