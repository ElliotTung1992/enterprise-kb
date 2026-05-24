package com.enterprise.kb.search.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * 知识库问答请求。
 *
 * @param question      用户问题
 * @param sessionId     会话 ID
 * @param modelProvider 模型提供商
 * @param modelName     模型名称
 * @param topK          检索返回数量
 */
public record QnARequest(
        @NotBlank String question,
        UUID sessionId,
        String modelProvider,
        String modelName,
        @Max(10) int topK
) {
    public QnARequest {
        topK = topK <= 0 ? 5 : topK;
    }
}
