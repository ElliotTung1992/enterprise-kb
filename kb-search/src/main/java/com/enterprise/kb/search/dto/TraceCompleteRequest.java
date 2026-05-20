package com.enterprise.kb.search.dto;

/**
 * 完成 Agent Trace 的请求。
 *
 * @param outputText    最终输出摘要或全文
 * @param rawOutputJson 完整输出快照 JSON
 * @param durationMs    总耗时毫秒
 * @param tokensUsed    Token 用量
 */
public record TraceCompleteRequest(
        String outputText,
        String rawOutputJson,
        Long durationMs,
        Integer tokensUsed
) {}
