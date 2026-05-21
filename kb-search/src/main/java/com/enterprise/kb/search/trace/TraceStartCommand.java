package com.enterprise.kb.search.trace;

import java.util.UUID;

/**
 * 创建 Trace Scope 的命令。
 *
 * @param traceType     Trace 类型
 * @param agentName     Agent 名称
 * @param sessionId     会话 ID
 * @param spaceId       知识空间 ID
 * @param userId        用户 ID
 * @param requestId     请求关联 ID
 * @param modelProvider 模型提供商
 * @param inputText     输入文本
 * @param rawInput      原始输入对象
 */
public record TraceStartCommand(
        String traceType,
        String agentName,
        UUID sessionId,
        UUID spaceId,
        UUID userId,
        String requestId,
        String modelProvider,
        String inputText,
        Object rawInput
) {}
