package com.enterprise.kb.search.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Agent Trace 中的单个步骤记录。
 */
@Getter
@Setter
public class AgentTraceStep {

    private UUID id;
    /** 所属 Trace ID */
    private UUID traceId;
    /** Trace 内步骤顺序 */
    private int stepIndex;
    /** 步骤类型：MODEL_CALL / TOOL_CALL / ROUTER / GUARD 等 */
    private String stepType;
    /** Agent 名称 */
    private String agentName;
    /** LLM 生成的工具调用 ID */
    private String toolCallId;
    /** 工具名称 */
    private String toolName;
    /** 步骤状态：SUCCEEDED / FAILED / SKIPPED / INTERRUPTED */
    private String status;
    /** 结构化入参 JSON */
    private String inputJson;
    /** 结构化出参 JSON */
    private String outputJson;
    /** 完整原始载荷 JSON */
    private String rawPayloadJson;
    /** 关联业务对象类型 */
    private String businessRefType;
    /** 关联业务对象 ID */
    private UUID businessRefId;
    /** 步骤耗时毫秒 */
    private Long durationMs;
    /** 异常类型 */
    private String errorType;
    /** 异常摘要 */
    private String errorMessage;
    /** 原始载荷是否被截断 */
    private boolean payloadTruncated;
    /** 创建时间 */
    private Instant createdAt;
}
