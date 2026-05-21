package com.enterprise.kb.search.trace;

import java.util.UUID;

/**
 * Trace Step 事件。
 *
 * @param stepType        步骤类型
 * @param name            步骤名称
 * @param status          状态
 * @param input           入参对象
 * @param output          出参对象
 * @param rawPayload      原始载荷
 * @param toolCallId      工具调用 ID
 * @param businessRefType 业务对象类型
 * @param businessRefId   业务对象 ID
 * @param durationMs      耗时毫秒
 * @param error           异常
 */
public record TraceEvent(
        String stepType,
        String name,
        String status,
        Object input,
        Object output,
        Object rawPayload,
        String toolCallId,
        String businessRefType,
        UUID businessRefId,
        Long durationMs,
        Throwable error
) {

    /**
     * 成功工具调用事件。
     *
     * @param toolName   工具名
     * @param toolCallId 工具调用 ID
     * @param input      入参
     * @param output     出参
     * @param durationMs 耗时毫秒
     * @return Trace 事件
     */
    public static TraceEvent toolCallSucceeded(String toolName, String toolCallId, Object input, Object output,
                                               Long durationMs) {
        return new TraceEvent("TOOL_CALL", toolName, "SUCCEEDED", input, output, null,
                toolCallId, null, null, durationMs, null);
    }

    /**
     * 失败工具调用事件。
     *
     * @param toolName   工具名
     * @param toolCallId 工具调用 ID
     * @param input      入参
     * @param error      异常
     * @param durationMs 耗时毫秒
     * @return Trace 事件
     */
    public static TraceEvent toolCallFailed(String toolName, String toolCallId, Object input, Throwable error,
                                            Long durationMs) {
        return new TraceEvent("TOOL_CALL", toolName, "FAILED", input, null, null,
                toolCallId, null, null, durationMs, error);
    }

    /**
     * 成功模型调用事件。
     *
     * @param agentName  Agent 名称
     * @param input      入参
     * @param output     出参
     * @param durationMs 耗时毫秒
     * @return Trace 事件
     */
    public static TraceEvent modelCallSucceeded(String agentName, Object input, Object output, Long durationMs) {
        return new TraceEvent("MODEL_CALL", agentName, "SUCCEEDED", input, output, null,
                null, null, null, durationMs, null);
    }

    /**
     * 失败模型调用事件。
     *
     * @param agentName  Agent 名称
     * @param input      入参
     * @param error      异常
     * @param durationMs 耗时毫秒
     * @return Trace 事件
     */
    public static TraceEvent modelCallFailed(String agentName, Object input, Throwable error, Long durationMs) {
        return new TraceEvent("MODEL_CALL", agentName, "FAILED", input, null, null,
                null, null, null, durationMs, error);
    }
}
