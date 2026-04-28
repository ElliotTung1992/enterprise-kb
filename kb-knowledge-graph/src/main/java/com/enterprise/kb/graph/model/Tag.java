package com.enterprise.kb.graph.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class Tag {

    private UUID id;
    /** 所属知识空间 ID */
    private UUID spaceId;
    /** 标签名称 */
    private String name;
    /** URL 友好 slug（同一空间内唯一） */
    private String slug;
    /** 颜色（HEX，如 #4f46e5） */
    private String color;
    /** 父标签 ID（自关联，构建标签树） */
    private UUID parentId;
    /** 标签类型（TAG / CATEGORY / ENTITY / TOPIC） */
    private String tagType = "TAG";
    /** 标签描述 */
    private String description;
    /** 排序权重（升序） */
    private Integer sortOrder = 0;
    /** 创建时间 */
    private Instant createdAt = Instant.now();
    /** 最后更新时间 */
    private Instant updatedAt = Instant.now();
    /** 软删除时间，为 null 表示未删除 */
    private Instant deletedAt;
}
