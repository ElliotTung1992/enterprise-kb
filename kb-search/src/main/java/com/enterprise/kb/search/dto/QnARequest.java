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
 * @param deepThinking  是否开启「深度思考」（Agentic 流式：开则注入 {@code /think} 软开关展示推理旁白，默认关）
 */
public record QnARequest(
        @NotBlank String question,
        UUID sessionId,
        String modelProvider,
        String modelName,
        @Max(10) int topK,
        boolean deepThinking
) {
    public QnARequest {
        topK = topK <= 0 ? 5 : topK;
    }

    /**
     * 兼容旧 5 参构造（{@code deepThinking} 默认 false），保持既有调用点不变。
     */
    public QnARequest(String question, UUID sessionId, String modelProvider, String modelName, int topK) {
        this(question, sessionId, modelProvider, modelName, topK, false);
    }
}
