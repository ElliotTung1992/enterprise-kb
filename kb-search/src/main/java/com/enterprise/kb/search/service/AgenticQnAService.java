package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.QnARequest;
import com.enterprise.kb.search.dto.QnAResponse;

import java.util.UUID;

/**
 * Agentic RAG 问答服务接口。
 * <p>LLM 作为 Agent 自主决定检索策略：分析问题、决定搜索关键词、
 * 按需多轮检索、综合所有结果生成答案。
 * 与普通 RAG 的区别在于检索不再是固定的一次，而是由 LLM 动态驱动。</p>
 */
public interface AgenticQnAService {

    /**
     * Agentic RAG 问答（同步）。
     * <p>LLM 自主调用 searchKnowledgeBase 工具进行多轮检索，最终生成答案。</p>
     *
     * @param spaceId 空间 UUID
     * @param req     问答请求
     * @return 问答响应，citations 汇总所有轮次的检索结果
     */
    QnAResponse ask(UUID spaceId, QnARequest req);
}
