package com.enterprise.kb.search.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Agent 单轮请求 Trace 外壳。
 */
@Getter
@Setter
public class AgentTrace {

    private UUID id;
    /** 链路类型，如 AGENTIC_QA / STANDARD_QA / CUSTOMER_ASSISTANT */
    private String traceType;
    /** 业务会话 ID */
    private UUID sessionId;
    /** 所属知识空间 ID，客服全局会话可为空 */
    private UUID spaceId;
    /** 发起用户 ID */
    private UUID userId;
    /** 请求关联 ID，用于串联 HTTP 日志 */
    private String requestId;
    /** 模型提供商 */
    private String modelProvider;
    /** Trace 状态：RUNNING / SUCCEEDED / FAILED / INTERRUPTED */
    private String status;
    /** 用户输入摘要或全文 */
    private String inputText;
    /** 最终输出摘要或全文 */
    private String outputText;
    /** 完整输入快照 JSON */
    private String rawInputJson;
    /** 完整输出快照 JSON */
    private String rawOutputJson;
    /** 总耗时毫秒 */
    private Long durationMs;
    /** Token 用量，可为空 */
    private Integer tokensUsed;
    /** 异常类型 */
    private String errorType;
    /** 异常摘要 */
    private String errorMessage;
    /** 原始载荷是否被截断 */
    private boolean payloadTruncated;
    /** Trace 开始时间 */
    private Instant startedAt;
    /** Trace 完成时间 */
    private Instant completedAt;
    /** 创建时间 */
    private Instant createdAt;
}
