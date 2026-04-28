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
import com.enterprise.kb.search.service.HybridSearchService;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    /** 工具单次检索召回数 */
    private static final int TOOL_RECALL_SIZE = 10;
    /** 工具单次 Rerank 后保留数 */
    private static final int TOOL_RERANK_TOP_N = 3;

    private static final String AGENT_SYSTEM_PROMPT = """
            你是一个智能知识库问答助手，拥有 searchKnowledgeBase 工具可以查询知识库文档。

            工作流程：
            1. 收到问题后，分析需要哪些信息
            2. 调用 searchKnowledgeBase 工具检索相关文档（可多次调用，每次使用不同关键词）
            3. 工具返回的每条结果都有编号 [n]，综合所有结果后生成完整准确的答案
            4. 在答案中用 [n] 标注引用来源，例如：根据[1]的说明，……；具体步骤见[2][3]
            5. 仅基于检索到的文档内容作答；如果知识库中没有相关信息，明确告知用户

            搜索建议：
            - 复杂问题可拆分为多个子问题分别搜索
            - 第一次结果不满意时，换用更精准的关键词重试
            - 每次搜索关键词保持简洁，避免使用完整句子
            """;

    @Override
    public QnAResponse ask(UUID spaceId, QnARequest req) {
        UUID sessionId = req.sessionId() != null ? req.sessionId() : UUID.randomUUID();

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
                            log.debug("Agent 调用检索工具，query=\"{}\"", input.query());
                            List<SearchHit> candidates = hybridSearchService.search(spaceId,
                                    new SearchRequest(input.query(), TOOL_RECALL_SIZE, null, null)).hits();
                            List<SearchHit> reranked = rerankService.rerank(
                                    input.query(), candidates, TOOL_RERANK_TOP_N);

                            // 分配稳定引用编号：同一 chunk 跨多次调用编号不变
                            List<Integer> assignedNums = new ArrayList<>();
                            for (SearchHit hit : reranked) {
                                String key = hit.chunkId() != null ? hit.chunkId()
                                        : String.valueOf(hit.excerpt().hashCode());
                                int num = numByChunkId.computeIfAbsent(key, k -> nextNum.getAndIncrement());
                                bestHitByChunkId.merge(key, hit,
                                        (old, newer) -> newer.score() > old.score() ? newer : old);
                                assignedNums.add(num);
                            }
                            return formatHitsForLlm(input.query(), reranked, assignedNums);
                        })
                .description("根据关键词在知识库中检索相关文档片段，返回来源和内容摘要")
                .inputType(KnowledgeSearchInput.class)
                .build();

        // 构建 ReactAgent：每次请求按需创建，工具闭包持有当次 spaceId 和 allHits
        ChatClient chatClient = modelProviderResolver.resolveChatClient(req.modelProvider());
        ReactAgent reactAgent = ReactAgent.builder()
                .name("kb-search-agent")
                .chatClient(chatClient)
                .systemPrompt(AGENT_SYSTEM_PROMPT)
                .tools(searchTool)
                .build();

        // 从 Redis 加载历史会话，构建包含完整上下文的消息列表
        List<Message> history = redisChatMemory.get(sessionId.toString());
        List<Message> messages = new ArrayList<>(history);
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

    /** 单次工具返回的最大字符数，防止单次检索结果撑爆上下文 */
    private static final int MAX_TOOL_RESULT_CHARS = 4000;

    /**
     * 将检索结果格式化为带编号的 LLM 可读文本，作为工具调用的返回值。
     * 编号与最终 citations 数组的 citationNumber 一一对应，LLM 在回答时用 [n] 引用。
     */
    private String formatHitsForLlm(String query, List<SearchHit> hits, List<Integer> nums) {
        if (hits.isEmpty()) {
            return "未找到与「" + query + "」相关的文档内容。";
        }
        StringBuilder sb = new StringBuilder();
        int totalChars = 0;
        for (int i = 0; i < hits.size(); i++) {
            SearchHit h = hits.get(i);
            String entry = "[%d] 来源：%s（第%s页）\n内容：%s\n---\n".formatted(
                    nums.get(i),
                    h.documentTitle(),
                    h.pageNumber() != null ? h.pageNumber() : "未知",
                    h.excerpt());
            if (totalChars + entry.length() > MAX_TOOL_RESULT_CHARS) {
                log.warn("Tool result truncated at {} chars to prevent context overflow", totalChars);
                break;
            }
            sb.append(entry);
            totalChars += entry.length();
        }
        return sb.toString();
    }
}
