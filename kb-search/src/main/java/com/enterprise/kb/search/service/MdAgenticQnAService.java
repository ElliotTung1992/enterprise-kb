package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.QnARequest;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Markdown Agentic 问答服务。
 */
public interface MdAgenticQnAService {

    /**
     * 使用 ReAct 两工具模式完成 Markdown 多跳流式问答。
     *
     * @param spaceId 空间 ID
     * @param req     问答请求
     * @return token 流
     */
    Flux<String> askStream(UUID spaceId, QnARequest req);
}
