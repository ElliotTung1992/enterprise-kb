package com.enterprise.kb.search.service.impl;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.ai.RedisChatMemory;
import com.enterprise.kb.search.dto.Citation;
import com.enterprise.kb.search.dto.KnowledgeSearchInput;
import com.enterprise.kb.search.dto.QnARequest;
import com.enterprise.kb.search.dto.QnAResponse;
import com.enterprise.kb.search.dto.SearchHit;
import com.enterprise.kb.search.dto.SearchRequest;
import com.enterprise.kb.common.util.SecurityUtils;
import com.enterprise.kb.search.service.AgenticQnAService;
import com.enterprise.kb.search.service.AgenticTokenBudgetService;
import com.enterprise.kb.search.service.HybridSearchService;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.enterprise.kb.search.service.QaChatSessionService;
import com.enterprise.kb.search.service.RerankService;
import com.enterprise.kb.common.exception.KbException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agentic RAG 问答服务实现（基于 ReactAgent）。
 * <p>使用 Spring AI Alibaba {@link ReactAgent} 作为 Agent 运行框架，
 * 将知识库混合检索封装为工具，由 LLM 自主决定：搜什么、搜几次、何时停止（ReAct 循环）。
 * 每轮工具调用结果累积为 citations 一并返回前端。会话历史通过 Redis 持久化。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgenticQnAServiceImpl implements AgenticQnAService {

    private final ModelProviderResolver modelProviderResolver;
    private final HybridSearchService hybridSearchService;
    private final RerankService rerankService;
    private final RedisChatMemory redisChatMemory;
    private final QaChatSessionService qaChatSessionService;
    private final AgenticTokenBudgetService tokenBudget;

    /** 工具单次检索召回数 */
    private static final int TOOL_RECALL_SIZE = 10;
    /** 工具单次 Rerank 后保留数 */
    private static final int TOOL_RERANK_TOP_N = 3;

    private final Encoding encoding = com.knuddels.jtokkit.Encodings.newLazyEncodingRegistry()
            .getEncoding(EncodingType.CL100K_BASE);

    @Override
    public QnAResponse ask(UUID spaceId, QnARequest req) {
        UUID sessionId = req.sessionId() != null ? req.sessionId() : UUID.randomUUID();

        // 计算 token 预算：history 按实际大小分配（上限 40%），剩余全给 retrieval
        List<Message> rawHistory = redisChatMemory.get(sessionId.toString());
        AgenticTokenBudgetService.Budget budget = tokenBudget.compute(req.question(), rawHistory);
        List<Message> trimmedHistory = tokenBudget.compressHistory(rawHistory, budget.historyTokensMax());

        // chunkId → 当前最优 hit（跨多轮去重，保留最高 score）
        LinkedHashMap<String, SearchHit> bestHitByChunkId = new LinkedHashMap<>();
        // chunkId → 引用编号（首次出现时按顺序分配，后续调用不变）
        Map<String, Integer> numByChunkId = new HashMap<>();
        AtomicInteger nextNum = new AtomicInteger(1);

        // 将知识库混合检索封装为 LLM 可调用的工具
        // 通过闭包直接捕获 spaceId，无需 ToolContext 传递运行时状态
        FunctionToolCallback<KnowledgeSearchInput, String> searchTool = FunctionToolCallback.builder(
                        "searchKnowledgeBase",
                        (KnowledgeSearchInput input) -> {
                            log.debug("Agent 调用检索工具，query=\"{}\".", input.query());
                            List<SearchHit> candidates = hybridSearchService.search(spaceId,
                                    new SearchRequest(input.query(), TOOL_RECALL_SIZE, null, null)).hits();
                            List<SearchHit> reranked = rerankService.rerank(
                                    input.query(), candidates, TOOL_RERANK_TOP_N);

                            // 按 retrievalSpace 截断：能放多少放多少，保底至少 1 条
                            List<SearchHit> withinBudget = new ArrayList<>();
                            int totalTokens = 0;
                            for (int i = 0; i < reranked.size(); i++) {
                                SearchHit hit = reranked.get(i);
                                int excerptTokens = encoding.encode(hit.excerpt()).size();
                                int hitTokens = excerptTokens + 20; // 来源/页码模板 token
                                if (totalTokens + hitTokens > budget.retrievalSpace() && i > 0) break;
                                withinBudget.add(hit);
                                totalTokens += hitTokens;
                            }

                            // 分配稳定引用编号：同一 chunk 跨多次调用编号不变
                            List<Integer> assignedNums = new ArrayList<>();
                            for (SearchHit hit : withinBudget) {
                                String key = hit.chunkId() != null ? hit.chunkId()
                                        : String.valueOf(hit.excerpt().hashCode());
                                int num = numByChunkId.computeIfAbsent(key, k -> nextNum.getAndIncrement());
                                bestHitByChunkId.merge(key, hit,
                                        (old, newer) -> newer.score() > old.score() ? newer : old);
                                assignedNums.add(num);
                            }
                            return formatHitsForLlm(withinBudget, assignedNums);
                        })
                .description("根据关键词在知识库中检索相关文档片段，返回来源和内容摘要")
                .inputType(KnowledgeSearchInput.class)
                .build();

        // 构建 ReactAgent：每次请求按需创建，工具闭包持有当次 spaceId 和 allHits
        ChatClient chatClient = modelProviderResolver.resolveChatClient(req.modelProvider());
        ReactAgent reactAgent = ReactAgent.builder()
                .name("kb-search-agent")
                .chatClient(chatClient)
                .systemPrompt(AgenticTokenBudgetService.AGENT_SYSTEM_PROMPT)
                .tools(searchTool)
                .build();

        // 构建消息列表：用截断后的 history，不影响 Redis 原始存储
        List<Message> messages = new ArrayList<>(trimmedHistory);
        messages.add(new UserMessage(req.question()));

        // 执行 ReactAgent：进入 ReAct 循环，LLM 自主驱动多轮 Reason + Act
        AssistantMessage assistantMessage;
        try {
            assistantMessage = reactAgent.call(messages);
        } catch (GraphRunnerException e) {
            log.error("Agentic RAG 执行失败：sessionId={}", sessionId, e);
            throw new KbException("Agent 执行失败：" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        String answer = assistantMessage.getText();

        // 将本轮对话追加持久化到 Redis
        redisChatMemory.add(sessionId.toString(),
                List.of(new UserMessage(req.question()), assistantMessage));

        // 按引用编号升序构建 citations，顺序与 LLM 答案中的 [n] 一一对应
        List<Citation> citations = numByChunkId.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(e -> {
                    SearchHit h = bestHitByChunkId.get(e.getKey());
                    return new Citation(e.getValue(), h.chunkId(), h.documentId(),
                            h.documentTitle(), h.excerpt(), h.pageNumber(), h.score());
                })
                .toList();

        log.info("Agentic RAG 完成：sessionId={}，引用文档块数={}", sessionId, citations.size());
        QnAResponse response = new QnAResponse(answer, sessionId, citations,
                req.modelProvider() != null ? req.modelProvider() : "DEFAULT", 0);
        // 异步持久化会话，失败不影响正常响应
        try {
            qaChatSessionService.saveExchange(sessionId, spaceId, SecurityUtils.getCurrentUserId(),
                    req.question(), answer);
        } catch (Exception e) {
            log.warn("保存会话记录失败：sessionId={}", sessionId, e);
        }
        return response;
    }

    private String formatHitsForLlm(List<SearchHit> hits, List<Integer> nums) {
        if (hits.isEmpty()) {
            return "未找到相关文档内容。";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            SearchHit h = hits.get(i);
            sb.append("[%d] 来源：%s（第%s页）\n内容：%s\n---\n".formatted(
                    nums.get(i),
                    h.documentTitle(),
                    h.pageNumber() != null ? h.pageNumber() : "未知",
                    h.excerpt()));
        }
        return sb.toString();
    }
}
