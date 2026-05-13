package com.enterprise.kb.search.dto;

import java.util.UUID;

/**
 * 商城客户助手对话响应
 *
 * @param answer    AI 回复内容
 * @param sessionId 会话 ID（新会话时由服务端生成）
 */
public record CustomerAssistantResponse(String answer, UUID sessionId) {}
