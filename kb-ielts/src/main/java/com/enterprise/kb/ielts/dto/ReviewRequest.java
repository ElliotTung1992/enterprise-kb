package com.enterprise.kb.ielts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * 提交复习评分请求
 *
 * @param recordId 学习记录 ID
 * @param rating   评分（AGAIN / HARD / GOOD / EASY）
 */
public record ReviewRequest(
        @NotNull UUID recordId,
        @NotBlank @Pattern(regexp = "AGAIN|HARD|GOOD|EASY", message = "rating 必须为 AGAIN/HARD/GOOD/EASY") String rating
) {}
