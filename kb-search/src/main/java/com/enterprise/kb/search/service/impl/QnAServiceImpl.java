package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.service.QnAService;
import com.enterprise.kb.document.mapper.DocumentMapper;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.dto.Citation;
import com.enterprise.kb.search.dto.QnARequest;
import com.enterprise.kb.search.dto.QnAResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/**
 * 问答服务实现，RAG 模式：先向量检索相关文档块，再调用 LLM 生成答案。
 * <p>支持同步阻塞和流式（SSE）两种响应模式。答案附带 citations 引用来源。
 * 使用 QuestionAnswerAdvisor 自动拼接检索上下文到 prompt。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QnAServiceImpl implements QnAService {

    private final ModelProviderResolver modelProviderResolver;
    private final VectorStore vectorStore;
    private final DocumentMapper documentMapper;

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
        ChatClient chatClient = modelProviderResolver.resolveChatClient(req.modelProvider());
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        SearchRequest searchReq = SearchRequest.builder()
                .topK(req.topK())
                .filterExpression(b.eq("spaceId", spaceId.toString()).build()).build();
        QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchReq).build();
        ChatResponse chatResponse = chatClient.prompt().advisors(qaAdvisor)
                .user(req.question()).call().chatResponse();
        String answer = chatResponse.getResult().getOutput().getText();
        String modelUsed = req.modelProvider() != null ? req.modelProvider() : "DEFAULT";
        int tokensUsed = 0;
        if (chatResponse.getMetadata() != null) {
            Usage usage = chatResponse.getMetadata().getUsage();
            if (usage != null) tokensUsed = (int) usage.getTotalTokens();
        }
        return new QnAResponse(answer, sessionId, extractCitations(chatResponse), modelUsed, tokensUsed);
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
        ChatClient chatClient = modelProviderResolver.resolveChatClient(req.modelProvider());
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        SearchRequest searchReq = SearchRequest.builder()
                .topK(req.topK())
                .filterExpression(b.eq("spaceId", spaceId.toString()).build()).build();
        QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchReq).build();
        return chatClient.prompt().advisors(qaAdvisor).user(req.question())
                .stream().content();
    }

    private List<Citation> extractCitations(ChatResponse chatResponse) {
        try {
            Object docs = chatResponse.getMetadata().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
            if (docs instanceof List<?> docList) {
                return docList.stream().filter(d -> d instanceof Document)
                        .map(d -> (Document) d)
                        .map(doc -> {
                            String docIdStr = (String) doc.getMetadata().get("documentId");
                            UUID documentId = docIdStr != null ? UUID.fromString(docIdStr) : null;
                            String title = documentId != null
                                    ? documentMapper.findByIdAndDeletedAtIsNull(documentId)
                                            .map(com.enterprise.kb.document.model.Document::getTitle)
                                            .orElse("Unknown") : "Unknown";
                            Object pageNum = doc.getMetadata().get("page_number");
                            Integer page = pageNum != null ? Integer.parseInt(pageNum.toString()) : null;
                            double score = doc.getScore() != null ? doc.getScore() : 0.0;
                            return new Citation(doc.getId(), documentId, title,
                                    truncate(doc.getText(), 200), page, score);
                        }).toList();
            }
        } catch (Exception e) {
            log.debug("Could not extract citations: {}", e.getMessage());
        }
        return List.of();
    }

    private String truncate(String text, int maxLen) {
        return text == null ? "" : (text.length() <= maxLen ? text : text.substring(0, maxLen) + "…");
    }
}
