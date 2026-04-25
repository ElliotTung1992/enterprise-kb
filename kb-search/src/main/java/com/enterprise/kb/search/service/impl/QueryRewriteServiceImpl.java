package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.service.QueryRewriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 查询改写服务实现，调用 LLM 将用户问题优化为更适合知识库检索的查询语句。
 * <p>改写失败时自动降级返回原始问题，不影响主流程。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewriteServiceImpl implements QueryRewriteService {

    private final ModelProviderResolver modelProviderResolver;

    private static final String REWRITE_SYSTEM_PROMPT = """
            你是一个搜索查询优化专家。请将用户的问题改写为适合知识库检索的查询语句。
            改写规则：
            1. 展开缩写和口语化表达（如"咋"→"如何"，"jwt"→"JWT token"）
            2. 补充相关专业术语，提高语义覆盖范围
            3. 去除语气词、无意义的修饰词
            4. 保持问题核心意图不变
            5. 只返回改写后的查询语句，不要任何解释或标点之外的内容
            """;

    /**
     * 将原始问题改写为更适合知识库检索的查询语句。
     *
     * @param question 用户原始问题
     * @return 改写后的检索查询，失败时返回原始问题
     */
    @Override
    public String rewrite(String question) {
        try {
            ChatClient chatClient = modelProviderResolver.resolveChatClient(null);
            String rewritten = chatClient.prompt()
                    .system(REWRITE_SYSTEM_PROMPT)
                    .user(question)
                    .call().content();
            if (rewritten == null || rewritten.isBlank()) {
                return question;
            }
            String result = rewritten.trim();
            log.debug("查询改写完成：原始=\"{}\" → 改写=\"{}\"", question, result);
            return result;
        } catch (Exception e) {
            log.warn("查询改写失败，降级使用原始问题，原因: {}", e.getMessage());
            return question;
        }
    }
}
