package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.QnARequest;
import com.enterprise.kb.search.dto.QnAResponse;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Markdown 结构感知问答服务。
 */
public interface MdQnAService {

    /**
     * 基于 Markdown 父子索引进行问答。
     *
     * @param spaceId 空间 ID
     * @param req     问答请求
     * @return 问答响应
     */
    QnAResponse ask(UUID spaceId, QnARequest req);

    /**
     * 基于 Markdown 父子索引进行流式问答。
     *
     * @param spaceId 空间 ID
     * @param req     问答请求
     * @return token 流
     */
    Flux<String> askStream(UUID spaceId, QnARequest req);
}
