package com.enterprise.kb.search.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * 商城客户助手对话请求
 *
 * @param sessionId     会话 ID（null 时自动创建新会话）
 * @param message       用户消息
 * @param modelProvider 指定模型提供商（null 使用默认）
 */
public record CustomerAssistantRequest(
        @JsonProperty("sessionId") UUID sessionId,
        @JsonProperty("message") String message,
        @JsonProperty("modelProvider") String modelProvider
) {}
