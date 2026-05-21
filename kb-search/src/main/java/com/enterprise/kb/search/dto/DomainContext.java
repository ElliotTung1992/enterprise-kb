package com.enterprise.kb.search.dto;

import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.UUID;

/**
 * 分派给 {@code DomainHandler} 的域内处理上下文。
 *
 * @param sessionId     会话 ID
 * @param userId        当前用户 ID
 * @param message       用户最新消息正文
 * @param history       对话历史，user / assistant 交替
 * @param modelProvider 请求指定的模型提供商，可为 {@code null}（用默认）
 * @param traceId       当前请求 Trace ID，可为 {@code null}
 */
public record DomainContext(UUID sessionId, UUID userId, String message,
                            List<Message> history, String modelProvider, UUID traceId) {

    public DomainContext(UUID sessionId, UUID userId, String message,
                         List<Message> history, String modelProvider) {
        this(sessionId, userId, message, history, modelProvider, null);
    }
}
