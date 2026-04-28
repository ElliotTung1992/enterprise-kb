package com.enterprise.kb.user.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class Role {

    private UUID id;
    /** 角色名，如 SYSTEM_ADMIN */
    private String name;
    /** 角色描述 */
    private String description;
    /** 创建时间 */
    private Instant createdAt = Instant.now();
}
