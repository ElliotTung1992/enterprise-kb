package com.enterprise.kb.search.dto;

import java.util.UUID;

/**
 * 记录 Agent Trace Step 的请求。
 *
 * @param stepIndex       Trace 内步骤顺序
 * @param stepType        步骤类型
 * @param agentName       Agent 名称
 * @param toolCallId      工具调用 ID
 * @param toolName        工具名称
 * @param status          步骤状态
 * @param inputJson       结构化入参 JSON
 * @param outputJson      结构化出参 JSON
 * @param rawPayloadJson  完整原始载荷 JSON
 * @param businessRefType 关联业务对象类型
 * @param businessRefId   关联业务对象 ID
 * @param durationMs      步骤耗时毫秒
 * @param errorType       异常类型
 * @param errorMessage    异常摘要
 */
public record TraceStepRequest(
        int stepIndex,
        String stepType,
        String agentName,
        String toolCallId,
        String toolName,
        String status,
        String inputJson,
        String outputJson,
        String rawPayloadJson,
        String businessRefType,
        UUID businessRefId,
        Long durationMs,
        String errorType,
        String errorMessage
) {}
