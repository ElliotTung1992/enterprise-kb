package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.QnARequest;
import com.enterprise.kb.search.dto.QnAResponse;

import java.util.UUID;

/**
 * Markdown Agentic 问答服务。
 */
public interface MdAgenticQnAService {

    /**
     * 使用 ReAct 两工具模式完成 Markdown 多跳问答。
     *
     * @param spaceId 空间 ID
     * @param req     问答请求
     * @return 问答响应
     */
    QnAResponse ask(UUID spaceId, QnARequest req);
}
