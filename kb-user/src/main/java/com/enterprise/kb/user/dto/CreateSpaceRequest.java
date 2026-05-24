package com.enterprise.kb.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建知识空间请求。
 *
 * @param name                   空间名称
 * @param slug                   空间标识
 * @param description            空间描述
 * @param preferredModelProvider 首选模型提供商
 */
public record CreateSpaceRequest(
        @NotBlank @Size(max = 255) String name,
        String slug,
        String description,
        String preferredModelProvider
) {}
