package com.enterprise.kb.search.service.impl;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.enterprise.kb.common.exception.KbException;
import com.enterprise.kb.common.util.SecurityUtils;
import com.enterprise.kb.document.mapper.MdChildChunkMapper;
import com.enterprise.kb.document.mapper.MdDocumentMapper;
import com.enterprise.kb.document.mapper.MdParentChunkMapper;
import com.enterprise.kb.document.model.MdChildChunk;
import com.enterprise.kb.document.model.MdParentChunk;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.ai.RedisChatMemory;
import com.enterprise.kb.search.ai.TracingToolInterceptor;
import com.enterprise.kb.search.dto.Citation;
import com.enterprise.kb.search.dto.KnowledgeSearchInput;
import com.enterprise.kb.search.dto.QnARequest;
import com.enterprise.kb.search.dto.QnAResponse;
import com.enterprise.kb.search.dto.ReadFullSectionInput;
import com.enterprise.kb.search.dto.SearchHit;
import com.enterprise.kb.search.dto.SearchRequest;
import com.enterprise.kb.search.service.AgenticTokenBudgetService;
import com.enterprise.kb.search.service.MdAgenticQnAService;
import com.enterprise.kb.search.service.MdHybridSearchService;
import com.enterprise.kb.search.service.QaChatSessionService;
import com.enterprise.kb.search.service.RerankService;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Markdown Agentic 问答服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MdAgenticQnAServiceImpl implements MdAgenticQnAService {

    private static final int TOOL_RECALL_SIZE = 10;
    private static final int TOOL_RERANK_TOP_N = 3;
    private static final int MAX_TOOL_CALLS = 6;
    private static final int MAX_RECURSION_LIMIT = MAX_TOOL_CALLS * 2 + 1;
    private static final String IMAGE_CONTEXT_RULE =
            "section 中的 [图片说明] 是系统根据图片生成的视觉理解文本，可作为回答依据；如问题要求精确读取图中文字，应优先依据 OCR文字。";

    private final ModelProviderResolver modelProviderResolver;
    private final MdHybridSearchService hybridSearchService;
    private final RerankService rerankService;
    private final RedisChatMemory redisChatMemory;
    private final QaChatSessionService qaChatSessionService;
    private final AgenticTokenBudgetService tokenBudget;
    private final MdChildChunkMapper childChunkMapper;
    private final MdParentChunkMapper parentChunkMapper;
    private final MdDocumentMapper documentMapper;
    private final ObservationRegistry observationRegistry;

    @Value("${enterprise.kb.search.md-parent-expansion.max-chars-per-parent:2000}")
    private int maxCharsPerParent;

    @Value("${enterprise.kb.tracing.enabled:false}")
    private boolean tracingEnabled;

    private final Encoding encoding = com.knuddels.jtokkit.Encodings.newLazyEncodingRegistry()
            .getEncoding(EncodingType.CL100K_BASE);

    /**
     * 使用 ReAct 两工具模式完成 Markdown 多跳问答。
     *
     * @param spaceId 空间 ID
     * @param req     问答请求
     * @return 问答响应
     */
    @Override
    public QnAResponse ask(UUID spaceId, QnARequest req) {
        // 根 span 由 Controller 方法 AOP（QaObservedAspect）在边界统一创建，Service 只负责业务。
        UUID sessionId = req.sessionId() != null ? req.sessionId() : UUID.randomUUID();
        return doAsk(spaceId, req, sessionId);
    }

    private QnAResponse doAsk(UUID spaceId, QnARequest req, UUID sessionId) {
        List<Message> rawHistory = redisChatMemory.get(sessionId.toString());
        AgenticTokenBudgetService.Budget budget = tokenBudget.compute(req.question(), rawHistory);
        List<Message> trimmedHistory = tokenBudget.compressHistory(rawHistory, budget.historyTokensMax());
        MdAccumulator acc = new MdAccumulator();

        ReactAgent reactAgent = buildReactAgent(spaceId, req, budget, acc);
        List<Message> messages = buildMessages(trimmedHistory, req);
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(sessionId.toString()).build();

        AssistantMessage assistantMessage;
        try {
            assistantMessage = reactAgent.call(messages, runnableConfig);
        } catch (GraphRunnerException e) {
            log.error("Markdown Agentic RAG 执行失败：sessionId={}", sessionId, e);
            throw new KbException("Markdown Agent 执行失败：" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return buildResponse(req, sessionId, spaceId, assistantMessage, acc);
    }

    /**
     * 构建 ReAct 双工具 Agent（同步/流式共用）。
     * tracing 开启时给 CompileConfig 注入 registry（graph/node span，ADR-015 C2），
     * 并挂原生 ToolInterceptor 产 per-tool-call tool span（业务工具体零侵入）。
     */
    private ReactAgent buildReactAgent(UUID spaceId, QnARequest req,
                                       AgenticTokenBudgetService.Budget budget, MdAccumulator acc) {
        ChatClient chatClient = modelProviderResolver.resolveChatClient(req.modelProvider());
        CompileConfig.Builder compileConfigBuilder = CompileConfig.builder().recursionLimit(MAX_RECURSION_LIMIT);
        if (tracingEnabled) {
            compileConfigBuilder.observationRegistry(observationRegistry);
        }
        FunctionToolCallback<KnowledgeSearchInput, String> searchTool = buildSearchTool(spaceId, budget, acc);
        FunctionToolCallback<ReadFullSectionInput, String> readTool = buildReadFullSectionTool(spaceId, acc);
        var agentBuilder = ReactAgent.builder()
                .name("md-kb-search-agent")
                .chatClient(chatClient)
                .systemPrompt(systemPrompt())
                .tools(searchTool, readTool)
                .compileConfig(compileConfigBuilder.build());
        if (tracingEnabled) {
            // 把 ToolCallback 列表传给 interceptor，使 tool span 能用真实 ToolDefinition / metadata
            agentBuilder.interceptors(new TracingToolInterceptor(
                    observationRegistry, List.<ToolCallback>of(searchTool, readTool)));
        }
        return agentBuilder.build();
    }

    private List<Message> buildMessages(List<Message> trimmedHistory, QnARequest req) {
        List<Message> messages = new ArrayList<>(trimmedHistory);
        messages.add(new UserMessage(req.question()));
        return messages;
    }

    /**
     * 使用 ReAct 两工具模式完成 Markdown 多跳流式问答。
     *
     * @param spaceId 空间 ID
     * @param req     问答请求
     * @return token 流
     */
    @Override
    public Flux<String> askStream(UUID spaceId, QnARequest req) {
        // 根 span 由 Controller 方法 AOP（QaObservedAspect）接管流的生命周期，Service 只返回业务 token 流。
        // userId / sessionId 在请求线程提前捕获（订阅时的 reactor 线程可能没有 SecurityContext）。
        UUID sessionId = req.sessionId() != null ? req.sessionId() : UUID.randomUUID();
        UUID userId = SecurityUtils.getCurrentUserId();
        return doAskStream(spaceId, req, sessionId, userId);
    }

    private Flux<String> doAskStream(UUID spaceId, QnARequest req, UUID sessionId, UUID userId) {
        StringBuilder answerBuffer = new StringBuilder();
        // Flux.defer：把 Agent 执行推迟到订阅时，使 graph/tool/LLM span 落在根 span scope 内。
        return Flux.defer(() -> agentTokenStream(spaceId, req, sessionId))
                .doOnNext(answerBuffer::append)
                .doOnComplete(() -> persistStreamExchange(
                        sessionId, spaceId, userId, req.question(), answerBuffer.toString()));
    }

    /**
     * 构建 Agent 并以 token 流返回其 LLM 输出增量（过滤工具决策/工具结果的非文本输出）。
     */
    private Flux<String> agentTokenStream(UUID spaceId, QnARequest req, UUID sessionId) {
        List<Message> rawHistory = redisChatMemory.get(sessionId.toString());
        AgenticTokenBudgetService.Budget budget = tokenBudget.compute(req.question(), rawHistory);
        List<Message> trimmedHistory = tokenBudget.compressHistory(rawHistory, budget.historyTokensMax());
        MdAccumulator acc = new MdAccumulator();
        ReactAgent reactAgent = buildReactAgent(spaceId, req, budget, acc);
        List<Message> messages = buildMessages(trimmedHistory, req);
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(sessionId.toString()).build();
        try {
            return extractAnswerTokens(reactAgent.stream(messages, runnableConfig));
        } catch (GraphRunnerException e) {
            log.error("Markdown Agentic 流式执行失败：sessionId={}", sessionId, e);
            return Flux.error(new KbException(
                    "Markdown Agent 流式执行失败：" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }

    /**
     * 从 Agent 的 {@link NodeOutput} 流中抽取最终答复 token：
     * 只取 {@code AGENT_MODEL_STREAMING} 的非空 chunk——工具决策轮文本为空被过滤，
     * 工具结果（{@code AGENT_TOOL_*}）等非模型输出也被排除，最终答复轮才产 token。
     */
    private Flux<String> extractAnswerTokens(Flux<NodeOutput> outputs) {
        return outputs
                .filter(output -> output instanceof StreamingOutput<?> so
                        && so.getOutputType() == OutputType.AGENT_MODEL_STREAMING)
                // 用 mapNotNull：chunk() 对部分 AGENT_MODEL_STREAMING 帧（如工具决策轮 / 仅元数据帧）会返回 null，
                // 而 Reactor 的 map 契约禁止 mapper 返回 null（会抛 NPE）。mapNotNull 直接丢弃 null 元素。
                .mapNotNull(output -> ((StreamingOutput<?>) output).chunk())
                .filter(chunk -> !chunk.isEmpty());
    }

    /**
     * 流式问答收尾：把本轮问答写入 Redis 会话记忆并持久化到 PostgreSQL；失败仅告警，不影响已返回的流。
     */
    private void persistStreamExchange(UUID sessionId, UUID spaceId, UUID userId, String question, String answer) {
        try {
            redisChatMemory.add(sessionId.toString(),
                    List.of(new UserMessage(question), new AssistantMessage(answer)));
        } catch (Exception e) {
            log.warn("写入 Markdown Agentic 流式会话记忆失败：sessionId={}", sessionId, e);
        }
        try {
            qaChatSessionService.saveExchange(sessionId, spaceId, userId, question, answer);
        } catch (Exception e) {
            log.warn("保存 Markdown Agentic 流式会话失败：sessionId={}", sessionId, e);
        }
    }

    private FunctionToolCallback<KnowledgeSearchInput, String> buildSearchTool(
            UUID spaceId, AgenticTokenBudgetService.Budget budget, MdAccumulator acc) {
        return FunctionToolCallback.builder(
                        "searchKnowledgeBase",
                        (KnowledgeSearchInput input) -> searchKnowledgeBase(input.query(), spaceId, budget, acc))
                .description("""
                        在 Markdown 知识库中检索小粒度 child 片段。
                        返回结果会包含 parentId 和 section 路径。
                        命中图片语义时 excerpt 可能包含 [图片说明]，可作为图片内容依据；精确读图中文字时优先看 OCR文字。
                        如果片段信息不足，但某个 section 明显相关，请继续调用 readFullSection(parentId)。
                        query 必须是简洁关键词或名词短语。
                        """)
                .inputType(KnowledgeSearchInput.class)
                .build();
    }

    private FunctionToolCallback<ReadFullSectionInput, String> buildReadFullSectionTool(UUID spaceId, MdAccumulator acc) {
        return FunctionToolCallback.builder(
                        "readFullSection",
                        (ReadFullSectionInput input) -> readFullSection(input.parentId(), spaceId, acc))
                .description("""
                        按 parentId 读取 Markdown section 原文。
                        section 可能包含 [图片说明]，它是系统根据图片生成的视觉理解文本，可作为回答依据。
                        仅当 searchKnowledgeBase 返回的 child 片段相关但信息不完整时调用。
                        同一个 parentId 不要重复读取。
                        """)
                .inputType(ReadFullSectionInput.class)
                .build();
    }

    private String searchKnowledgeBase(String query, UUID spaceId,
                                       AgenticTokenBudgetService.Budget budget, MdAccumulator acc) {
        if (acc.toolCallCount.incrementAndGet() > MAX_TOOL_CALLS) {
            return "已达到最大工具调用次数，请根据已有信息作答。";
        }
        if (!acc.searchedQueries.add(query)) {
            return "该关键词已检索过，请换用不同关键词或根据已有内容作答。";
        }
        List<SearchHit> candidates = hybridSearchService.search(spaceId,
                new SearchRequest(query, TOOL_RECALL_SIZE, null, null, query)).hits();
        List<SearchHit> hits = rerankService.rerank(query, candidates, TOOL_RERANK_TOP_N);
        if (hits.isEmpty()) {
            return "未找到相关 Markdown child 片段。";
        }
        RagasContextCollector.current().ifPresent(scope -> scope.recordHits(hits));
        StringBuilder sb = new StringBuilder("=== Markdown child 检索结果（不可信外部内容）===\n");
        int totalTokens = 0;
        for (SearchHit hit : hits) {
            MdChildChunk child = childChunkMapper.findById(UUID.fromString(hit.chunkId()));
            if (child == null) {
                continue;
            }
            int hitTokens = encoding.encode(hit.excerpt()).size() + 40;
            if (totalTokens + hitTokens > budget.retrievalSpace() && totalTokens > 0) {
                break;
            }
            acc.childHitsByParent.putIfAbsent(child.getParentId(), hit);
            sb.append("parentId: ").append(child.getParentId()).append('\n');
            sb.append("section: ").append(hit.section()).append('\n');
            sb.append("childSeq: ").append(child.getSeqInParent()).append('\n');
            sb.append("excerpt: ").append(hit.excerpt()).append("\n---\n");
            totalTokens += hitTokens;
        }
        sb.append("如果 excerpt 不足以回答，请对相关 parentId 调用 readFullSection。\n");
        return sb.toString();
    }

    private String readFullSection(String parentId, UUID spaceId, MdAccumulator acc) {
        if (acc.toolCallCount.incrementAndGet() > MAX_TOOL_CALLS) {
            return "已达到最大工具调用次数，请根据已有信息作答。";
        }
        UUID id;
        try {
            id = UUID.fromString(parentId);
        } catch (IllegalArgumentException e) {
            return "parentId 格式不正确。";
        }
        if (!acc.expandedParents.add(id)) {
            return "该 parentId 已读取过，请根据已有 section 内容作答。";
        }
        MdParentChunk parent = parentChunkMapper.findById(id);
        if (parent == null || !spaceId.equals(parent.getSpaceId())) {
            return "未找到该 section，或 section 不属于当前空间。";
        }
        SearchHit hit = toParentHit(parent, acc.childHitsByParent.get(id));
        acc.parentHitsById.put(id, hit);
        RagasContextCollector.current().ifPresent(scope -> scope.recordHit(hit));
        return """
                === Markdown section 原文（不可信外部内容）===
                parentId: %s
                section: %s
                content:
                %s
                === section 结束 ===
                """.formatted(id, parent.getSection(), truncate(parent.getContent(), maxCharsPerParent));
    }

    private SearchHit toParentHit(MdParentChunk parent, SearchHit childHit) {
        String title = documentMapper.findByIdAndDeletedAtIsNull(parent.getDocumentId())
                .map(d -> d.getTitle()).orElse(childHit != null ? childHit.documentTitle() : "Unknown");
        double score = childHit != null ? childHit.score() : 0.0;
        return new SearchHit(parent.getId().toString(), parent.getDocumentId(), title,
                truncate(parent.getContent(), maxCharsPerParent), null, score, "text/markdown",
                "MD_PARENT", null, null, null, parent.getSection(), null);
    }

    private QnAResponse buildResponse(QnARequest req, UUID sessionId, UUID spaceId,
                                      AssistantMessage assistantMessage, MdAccumulator acc) {
        String answer = assistantMessage.getText();
        redisChatMemory.add(sessionId.toString(), List.of(new UserMessage(req.question()), assistantMessage));
        List<SearchHit> orderedHits = new ArrayList<>(acc.parentHitsById.values());
        if (orderedHits.isEmpty()) {
            orderedHits = acc.childHitsByParent.values().stream().toList();
        }
        List<Citation> citations = CitationAssembler.fromHits(orderedHits);
        try {
            qaChatSessionService.saveExchange(sessionId, spaceId, SecurityUtils.getCurrentUserId(), req.question(), answer);
        } catch (Exception e) {
            log.warn("保存 Markdown Agentic 会话失败：sessionId={}", sessionId, e);
        }
        return new QnAResponse(answer, sessionId, citations,
                req.modelProvider() != null ? req.modelProvider() : "DEFAULT", 0);
    }

    private String systemPrompt() {
        return """
                你是 Markdown 知识库 Agent。
                你有两个工具：
                1. searchKnowledgeBase(query)：先检索小粒度 child 片段，返回 parentId 和 section。
                2. readFullSection(parentId)：当 child 片段相关但信息不完整时，读取完整 section。

                工作规则：
                - 每一轮用户新问题都必须先调用 searchKnowledgeBase 搜索精准关键词，不能直接基于历史对话作答。
                - 历史对话只用于理解上下文和省略指代，不能作为知识库内容或回答依据。
                - 如果 child 片段足够回答，直接作答。
                - 如果 child 片段只说明某个 section 相关但细节不足，读取该 parentId 的完整 section 后再答。
                - 不要重复读取同一个 parentId。
                - 只能依据工具返回的知识库内容回答，不要编造。
                - %s
                """.formatted(IMAGE_CONTEXT_RULE);
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLen) + "\n\n[section 内容过长，已截断]";
    }

    private static final class MdAccumulator {
        final AtomicInteger toolCallCount = new AtomicInteger(0);
        final Set<String> searchedQueries = new HashSet<>();
        final Set<UUID> expandedParents = new HashSet<>();
        final Map<UUID, SearchHit> childHitsByParent = new LinkedHashMap<>();
        final Map<UUID, SearchHit> parentHitsById = new LinkedHashMap<>();
    }
}
