package com.enterprise.kb.search.dto;

import java.util.List;
import java.util.UUID;

/**
 * 知识库问答响应。
 *
 * @param answer     答案文本
 * @param sessionId  会话 ID
 * @param citations  引用来源列表
 * @param modelUsed  实际使用模型
 * @param tokensUsed 消耗 token 数
 */
public record QnAResponse(
        String answer,
        UUID sessionId,
        List<Citation> citations,
        String modelUsed,
        int tokensUsed
) {}
