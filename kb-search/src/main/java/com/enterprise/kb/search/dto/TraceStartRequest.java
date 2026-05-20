package com.enterprise.kb.search.dto;

import java.util.UUID;

/**
 * 创建 Agent Trace 的请求。
 *
 * @param traceType     链路类型
 * @param sessionId     会话 ID
 * @param spaceId       空间 ID
 * @param userId        用户 ID
 * @param requestId     请求关联 ID
 * @param modelProvider 模型提供商
 * @param inputText     用户输入摘要或全文
 * @param rawInputJson  完整输入快照 JSON
 */
public record TraceStartRequest(
        String traceType,
        UUID sessionId,
        UUID spaceId,
        UUID userId,
        String requestId,
        String modelProvider,
        String inputText,
        String rawInputJson
) {}
