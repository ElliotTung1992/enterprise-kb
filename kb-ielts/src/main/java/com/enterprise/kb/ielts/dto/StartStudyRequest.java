package com.enterprise.kb.ielts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 开始学习某条内容的请求
 *
 * @param contentType 内容类型（WORD/PHRASE/PARAPHRASE 等）
 * @param contentId   内容 ID
 */
public record StartStudyRequest(
        @NotBlank String contentType,
        @NotNull UUID contentId
) {}
