package com.enterprise.kb.search.service;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Agentic RAG 的 token 预算服务。
 *
 * <p>负责根据模型 context limit 动态分配：history 按实际大小分配，上限为可用空间的
 * {@code MAX_HISTORY_RATIO}，剩余全部给 retrieval。
 *
 * <p>主方法 {@link #compute(String, List, String)} 只返回预算数据，
 * 不持有任何业务状态，可独立测试。
 */
public interface AgenticTokenBudgetService {

    /** searchKnowledgeBase 工具 schema（用于启动时预计算 token 数） */
    String TOOL_SCHEMA = """
            {"name":"searchKnowledgeBase","description":"根据精准关键词在知识库中检索相关文档片段。query 必须是简洁的名词短语或核心概念，不要包含问句或完整句子。如果问题包含多个独立概念，拆分为多次调用分别检索。如果返回结果与问题明显无关，换用更精准的关键词重新搜索。","inputSchema":{"type":"object","properties":{"query":{"type":"string","description":"搜索查询词，简洁名词短语"}},"required":["query"]}}
            """;

    /**
     * 根据当次请求动态计算可用预算。history 实际 token 数决定分配量，超过上限时触发压缩。
     *
     * @param question     当前问题
     * @param history      Redis 中的原始历史（用于计算实际 token 占用）
     * @param systemPrompt 本次实际发送给 Agent 的 system prompt
     * @return 调整后的 budget
     */
    Budget compute(String question, List<Message> history, String systemPrompt);

    /**
     * 按 budget 压缩 history：最新 N 条消息原样保留，更早的消息通过 LLM 摘要压缩为一条
     * SystemMessage，不改变 Redis 原始存储。
     *
     * @param history   Redis 中的原始历史
     * @param maxTokens history 最大 token 数
     * @return 压缩后的 history 列表
     */
    List<Message> compressHistory(List<Message> history, int maxTokens);

    record Budget(int historyTokensMax, int retrievalSpace) {}
}
