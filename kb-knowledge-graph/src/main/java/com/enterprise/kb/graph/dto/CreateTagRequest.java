package com.enterprise.kb.graph.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 创建标签请求。
 *
 * @param name        标签名称
 * @param slug        标签标识
 * @param color       标签颜色
 * @param parentId    父标签 ID
 * @param tagType     标签类型
 * @param description 标签描述
 * @param sortOrder   排序值
 */
public record CreateTagRequest(
        @NotBlank @Size(max = 200) String name,
        String slug,
        String color,
        UUID parentId,
        String tagType,
        String description,
        Integer sortOrder
) {}
