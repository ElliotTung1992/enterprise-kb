package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.service.HydeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * HyDE 服务实现，调用 LLM 生成假设性回答文档用于向量检索。
 * <p>原理：假设文档与知识库真实答案文档语义更相近，
 * 用假设文档的向量检索比原始问题向量召回更准确。
 * 生成失败时自动降级，不影响主流程。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HydeServiceImpl implements HydeService {

    private final ModelProviderResolver modelProviderResolver;

    private static final String HYDE_SYSTEM_PROMPT = """
            假设你已经知道以下问题的答案，请生成一段可能出现在专业技术文档中的回答文本。
            要求：
            1. 使用专业术语和文档风格，而非对话风格
            2. 内容简洁，100~200字以内
            3. 直接给出回答内容，不要加"根据文档"等前缀
            4. 不要解释你在做什么，只返回假设性的文档内容
            """;

    /**
     * 根据用户问题生成假设性回答文档。
     *
     * @param question 用户原始问题
     * @return 假设性回答文档，失败时返回原始问题
     */
    @Override
    public String generateHypotheticalDocument(String question) {
        try {
            ChatClient chatClient = modelProviderResolver.resolveChatClient(null);
            String hypotheticalDoc = chatClient.prompt()
                    .system(HYDE_SYSTEM_PROMPT)
                    .user(question)
                    .call().content();
            if (hypotheticalDoc == null || hypotheticalDoc.isBlank()) {
                return question;
            }
            String result = hypotheticalDoc.trim();
            log.debug("HyDE 生成假设文档：问题=\"{}\" → 假设文档=\"{}\"", question, result);
            return result;
        } catch (Exception e) {
            log.warn("HyDE 假设文档生成失败，降级使用原始问题，原因: {}", e.getMessage());
            return question;
        }
    }
}
