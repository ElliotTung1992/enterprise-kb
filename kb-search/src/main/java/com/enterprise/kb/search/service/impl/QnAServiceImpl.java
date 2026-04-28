package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.service.QnAService;
import com.enterprise.kb.search.service.HybridSearchService;
import com.enterprise.kb.search.service.HydeService;
import com.enterprise.kb.search.service.QueryRewriteService;
import com.enterprise.kb.search.service.RerankService;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.ai.RedisChatMemory;
import com.enterprise.kb.search.dto.Citation;
import com.enterprise.kb.search.dto.QnARequest;
import com.enterprise.kb.search.dto.QnAResponse;
import com.enterprise.kb.search.dto.SearchHit;
import com.enterprise.kb.search.dto.SearchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 问答服务实现，RAG 模式：先向量检索相关文档块，再调用 LLM 生成答案。
 * <p>支持同步阻塞和流式（SSE）两种响应模式。答案附带 citations 引用来源。
 * 使用 QuestionAnswerAdvisor 自动拼接检索上下文到 prompt。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QnAServiceImpl implements QnAService {

    /** 混合检索召回倍数：召回 topK×RECALL_MULTIPLIER 条交给 Rerank 精排 */
    private static final int RECALL_MULTIPLIER = 4;

    private final ModelProviderResolver modelProviderResolver;
    private final HybridSearchService hybridSearchService;
    private final QueryRewriteService queryRewriteService;
    private final HydeService hydeService;
    private final RerankService rerankService;
    private final RedisChatMemory redisChatMemory;

    /**
     * 同步 RAG 问答。
     *
     * @param spaceId 空间 UUID
     * @param req     问答请求
     * @return 问答响应
     */
    @Override
    public QnAResponse ask(UUID spaceId, QnARequest req) {
        UUID sessionId = req.sessionId() != null ? req.sessionId() : UUID.randomUUID();

        // Pre-retrieval 阶段：查询改写 + HyDE 假设文档生成，两者并行提升检索质量
        String retrievalQuery = queryRewriteService.rewrite(req.question());
        // HyDE：用假设文档向量做语义检索；关键词检索仍使用改写后的 retrievalQuery
        String hypotheticalDoc = hydeService.generateHypotheticalDocument(retrievalQuery);
        // 混合检索：语义路用假设文档(semanticQuery)，关键词路用改写查询(query)；召回量扩大供 Rerank 精排
        int recallSize = req.topK() * RECALL_MULTIPLIER;
        List<SearchHit> candidates = hybridSearchService.search(spaceId,
                new SearchRequest(retrievalQuery, recallSize, req.modelProvider(), null, hypotheticalDoc)).hits();
        // Post-retrieval：Rerank 精排，从候选中取最相关的 topK 条
        List<SearchHit> hits = rerankService.rerank(req.question(), candidates, req.topK());

        ChatClient chatClient = modelProviderResolver.resolveChatClient(req.modelProvider());
        // 按 sessionId 加载/保存多轮对话历史；MessageChatMemoryAdvisor 在请求前注入历史，在响应后自动存储
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(redisChatMemory)
                .conversationId(sessionId.toString()).build();

        ChatResponse chatResponse = chatClient.prompt()
                .advisors(memoryAdvisor)
                .system(buildSystemPrompt(hits))
                .user(req.question())
                .call().chatResponse();

        String answer = chatResponse.getResult().getOutput().getText();
        String modelUsed = req.modelProvider() != null ? req.modelProvider() : "DEFAULT";
        int tokensUsed = 0;
        if (chatResponse.getMetadata() != null) {
            Usage usage = chatResponse.getMetadata().getUsage();
            if (usage != null) tokensUsed = (int) usage.getTotalTokens();
        }
        // citations 直接从检索结果映射，无需再查数据库
        List<Citation> citations = IntStream.range(0, hits.size())
                .mapToObj(i -> {
                    SearchHit h = hits.get(i);
                    return new Citation(i + 1, h.chunkId(), h.documentId(),
                            h.documentTitle(), h.excerpt(), h.pageNumber(), h.score());
                })
                .toList();
        return new QnAResponse(answer, sessionId, citations, modelUsed, tokensUsed);
    }

    /**
     * 流式 RAG 问答。
     *
     * @param spaceId 空间 UUID
     * @param req     问答请求
     * @return token 字符串流
     */
    @Override
    public Flux<String> askStream(UUID spaceId, QnARequest req) {
        String retrievalQuery = queryRewriteService.rewrite(req.question());
        String hypotheticalDoc = hydeService.generateHypotheticalDocument(retrievalQuery);
        int recallSize = req.topK() * RECALL_MULTIPLIER;
        List<SearchHit> candidates = hybridSearchService.search(spaceId,
                new SearchRequest(retrievalQuery, recallSize, req.modelProvider(), null, hypotheticalDoc)).hits();
        List<SearchHit> hits = rerankService.rerank(req.question(), candidates, req.topK());
        ChatClient chatClient = modelProviderResolver.resolveChatClient(req.modelProvider());
        return chatClient.prompt()
                .system(buildSystemPrompt(hits))
                .user(req.question())
                .stream().content();
    }

    /**
     * 将混合检索结果拼装成 RAG system prompt，注入知识库上下文。
     * 检索为空时告知 LLM 无相关文档，避免幻觉。
     */
    private String buildSystemPrompt(List<SearchHit> hits) {
        String context = hits.isEmpty()
                ? "（未检索到相关文档内容）"
                : hits.stream()
                        .map(h -> "来源：%s（第%s页）\n内容：%s".formatted(
                                h.documentTitle(),
                                h.pageNumber() != null ? h.pageNumber() : "未知",
                                h.excerpt()))
                        .collect(Collectors.joining("\n---\n"));
        return """
                你是一个专业的知识库问答助手。请严格根据以下参考文档内容回答用户问题。
                如果参考文档中没有足够的信息，请明确说明无法从知识库中找到相关答案，不要编造内容。

                参考文档：
                %s
                """.formatted(context);
    }
}
