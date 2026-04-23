package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.QnARequest;
import com.enterprise.kb.search.dto.QnAResponse;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * 问答服务接口，RAG 模式：向量检索 + LLM 生成，支持同步和流式两种响应模式。
 */
public interface QnAService {

    /**
     * Ask a question and get a synchronous response with citations.
     *
     * @param spaceId the space ID
     * @param req     the QnA request containing the question
     * @return the QnA response with answer and metadata
     */
    QnAResponse ask(UUID spaceId, QnARequest req);

    /**
     * Ask a question and get a streaming response.
     *
     * @param spaceId the space ID
     * @param req     the QnA request containing the question
     * @return a Flux of string tokens for streaming response
     */
    Flux<String> askStream(UUID spaceId, QnARequest req);
}
