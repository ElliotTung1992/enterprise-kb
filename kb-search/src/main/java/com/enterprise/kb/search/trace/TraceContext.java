package com.enterprise.kb.search.trace;

import java.util.UUID;

/**
 * Trace 运行上下文。
 *
 * @param traceId   Trace ID
 * @param traceType Trace 类型
 * @param agentName Agent 名称
 * @param sessionId 会话 ID
 * @param spaceId   知识空间 ID
 * @param userId    用户 ID
 * @param enabled   是否启用
 */
public record TraceContext(
        UUID traceId,
        String traceType,
        String agentName,
        UUID sessionId,
        UUID spaceId,
        UUID userId,
        boolean enabled
) {

    private static final TraceContext NOOP = new TraceContext(null, null, null, null, null, null, false);

    /**
     * 空 Trace 上下文。
     *
     * @return 空上下文
     */
    public static TraceContext noop() {
        return NOOP;
    }
}
