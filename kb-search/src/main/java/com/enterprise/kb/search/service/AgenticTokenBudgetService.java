package com.enterprise.kb.search.service;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Agentic RAG 的 token 预算服务。
 *
 * <p>负责根据模型 context limit 动态分配：history 按实际大小分配，上限为可用空间的
 * {@code MAX_HISTORY_RATIO}，剩余全部给 retrieval。
 *
 * <p>主方法 {@link #compute(String, List)} 只返回预算数据，
 * 不持有任何业务状态，可独立测试。
 */
public interface AgenticTokenBudgetService {

    /** Agent 系统提示词（同时用于 token 预算计算和 ReactAgent 构建） */
    String AGENT_SYSTEM_PROMPT = """
            你是一个智能知识库问答助手，拥有 searchKnowledgeBase 工具可以查询知识库文档。

            工作流程：
            1. 收到问题后，分析需要哪些信息
            2. 调用 searchKnowledgeBase 工具检索相关文档（可多次调用，每次使用不同关键词）
            3. 工具返回的每条结果都有编号 [n]，综合所有结果后生成完整准确的答案
            4. 在答案中用 [n] 标注引用来源，例如：根据[1]的说明，……；具体步骤见[2][3]
            5. 仅基于检索到的文档内容作答；如果知识库中没有相关信息，明确告知用户

            搜索建议：
            - 复杂问题可拆分为多个子问题分别搜索
            - 第一次结果不满意时，换用更精准的关键词重试
            - 每次搜索关键词保持简洁，避免使用完整句子
            """;

    /** searchKnowledgeBase 工具 schema（用于启动时预计算 token 数） */
    String TOOL_SCHEMA = """
            {"name":"searchKnowledgeBase","description":"根据关键词在知识库中检索相关文档片段，返回来源和内容摘要","inputSchema":{"type":"object","properties":{"query":{"type":"string","description":"搜索查询词"}},"required":["query"]}}
            """;

    /**
     * 根据当次请求动态计算可用预算。history 实际 token 数决定分配量，超过上限时触发压缩。
     *
     * @param question 当前问题
     * @param history  Redis 中的原始历史（用于计算实际 token 占用）
     * @return 调整后的 budget
     */
    Budget compute(String question, List<Message> history);

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