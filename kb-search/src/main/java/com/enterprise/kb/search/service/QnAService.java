package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.QnARequest;
import com.enterprise.kb.search.dto.QnAResponse;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Question and Answer service using a chat model with vector store retrieval.
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
