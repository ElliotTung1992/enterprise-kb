package com.enterprise.kb.search.service.impl;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.enterprise.kb.common.constants.ModelProvider;
import com.enterprise.kb.common.exception.KbException;
import com.enterprise.kb.common.prompt.PromptProvider;
import com.enterprise.kb.common.util.SecurityUtils;
import com.enterprise.kb.document.mapper.MdChildChunkMapper;
import com.enterprise.kb.document.mapper.MdDocumentMapper;
import com.enterprise.kb.document.mapper.MdParentChunkMapper;
import com.enterprise.kb.document.model.MdChildChunk;
import com.enterprise.kb.document.model.MdParentChunk;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.ai.RedisChatMemory;
import com.enterprise.kb.search.ai.TracingToolInterceptor;
import com.enterprise.kb.search.dto.AgentStreamEvent;
import com.enterprise.kb.search.dto.Citation;
import com.enterprise.kb.search.dto.KnowledgeSearchInput;
import com.enterprise.kb.search.dto.QnARequest;
import com.enterprise.kb.search.dto.ReadFullSectionInput;
import com.enterprise.kb.search.dto.SearchHit;
import com.enterprise.kb.search.dto.SearchRequest;
import com.enterprise.kb.search.service.AgenticTokenBudgetService;
import com.enterprise.kb.search.service.MdAgenticQnAService;
import com.enterprise.kb.search.service.MdHybridSearchService;
import com.enterprise.kb.search.service.QaChatSessionService;
import com.enterprise.kb.search.service.RerankService;
import com.enterprise.kb.user.service.ProfileService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Collections;
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
    private static final String AGENTIC_SYSTEM_PROMPT = "kb/agentic/system";
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
    /** 用户画像：在线读，渲染 <user_profile> 软默认块注入 Agent 系统 prompt（ADR-016）。 */
    private final ProfileService profileService;
    private final PromptProvider promptProvider;

    @Value("${enterprise.kb.search.md-parent-expansion.max-chars-per-parent:2000}")
    private int maxCharsPerParent;

    @Value("${enterprise.kb.tracing.enabled:false}")
    private boolean tracingEnabled;

    @Value("${enterprise.kb.ai.default-provider:LLAMA_CPP}")
    private String defaultChatProvider = ModelProvider.LLAMA_CPP.name();

    private final Encoding encoding = com.knuddels.jtokkit.Encodings.newLazyEncodingRegistry()
            .getEncoding(EncodingType.CL100K_BASE);

    /** 解析工具调用 arguments JSON（提取 query / parentId）。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构建 ReAct 双工具 Agent（同步/流式共用）。
     * tracing 开启时给 CompileConfig 注入 registry（graph/node span，ADR-015 C2），
     * 并挂原生 ToolInterceptor 产 per-tool-call tool span（业务工具体零侵入）。
     */
    private ReactAgent buildReactAgent(UUID spaceId, QnARequest req,
                                       AgenticTokenBudgetService.Budget budget, MdAccumulator acc, String systemPrompt) {
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
                .systemPrompt(systemPrompt)
                .tools(searchTool, readTool)
                .compileConfig(compileConfigBuilder.build());
        if (tracingEnabled) {
            // 把 ToolCallback 列表传给 interceptor，使 tool span 能用真实 ToolDefinition / metadata
            agentBuilder.interceptors(new TracingToolInterceptor(
                    observationRegistry, List.<ToolCallback>of(searchTool, readTool)));
        }
        return agentBuilder.build();
    }

    /**
     * 流式 agentic：按 deepThinking 注入 Qwen3 {@code /think}·{@code /no_think} 软开关，
     * 但仅对识别该语法的 llama.cpp(Qwen3) 生效——DashScope / OpenAI 等不注入，避免字面量泄漏给模型。
     * 软开关只进发给模型的「本轮」消息；持久化与历史仍用 {@code req.question()} 原文。
     */
    private List<Message> buildStreamMessages(List<Message> trimmedHistory, QnARequest req) {
        String question = req.question();
        if (supportsThinkingSwitch(req.modelProvider())) {
            question += req.deepThinking() ? " /think" : " /no_think";
        }
        return appendUserMessage(trimmedHistory, question);
    }

    private List<Message> appendUserMessage(List<Message> trimmedHistory, String text) {
        List<Message> messages = new ArrayList<>(trimmedHistory);
        messages.add(new UserMessage(text));
        return messages;
    }

    /** 仅 llama.cpp(Qwen3) 识别 /think·/no_think；null/空先解析为配置中的默认 provider。 */
    private boolean supportsThinkingSwitch(String modelProvider) {
        String provider = (modelProvider != null && !modelProvider.isBlank()) ? modelProvider : defaultChatProvider;
        return ModelProvider.LLAMA_CPP.name().equalsIgnoreCase(provider);
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
        // 画像在请求线程预渲染：订阅时的 reactor 线程可能没有 SecurityContext。
        String profileBlock = profileService.renderProfileBlock(userId);
        StringBuilder answerBuffer = new StringBuilder();
        // Flux.defer：把 Agent 执行推迟到订阅时，使 graph/tool/LLM span 落在根 span scope 内。
        // 流里混了 thinking / 工具事件 / 答案三类 JSON 事件，持久化只累加 type==answer 的 delta，保证历史干净。
        return Flux.defer(() -> agentTokenStream(spaceId, req, sessionId, profileBlock))
                .doOnNext(event -> answerBuffer.append(AgentStreamEvent.answerDelta(event)))
                .doOnComplete(() -> {
                    if (!answerBuffer.isEmpty()) {
                        persistStreamExchange(sessionId, spaceId, userId, req.question(), answerBuffer.toString());
                    }
                });
    }

    /**
     * 构建 Agent 并把其 {@link NodeOutput} 流映射为「过程事件」流（thinking / tool_call / tool_result / answer / done / error）。
     */
    private Flux<String> agentTokenStream(UUID spaceId, QnARequest req, UUID sessionId, String profileBlock) {
        List<Message> rawHistory = redisChatMemory.get(sessionId.toString());
        String systemPrompt = composeSystemPrompt(profileBlock);
        AgenticTokenBudgetService.Budget budget = tokenBudget.compute(req.question(), rawHistory, systemPrompt);
        List<Message> trimmedHistory = tokenBudget.compressHistory(rawHistory, budget.historyTokensMax());
        MdAccumulator acc = new MdAccumulator();
        ReactAgent reactAgent = buildReactAgent(spaceId, req, budget, acc, systemPrompt);
        List<Message> messages = buildStreamMessages(trimmedHistory, req);
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(sessionId.toString()).build();
        try {
            return mapToEventStream(reactAgent.stream(messages, runnableConfig), acc);
        } catch (GraphRunnerException e) {
            log.error("Markdown Agentic 流式执行失败：sessionId={}", sessionId, e);
            // 先发结构化 error 事件给前端，再重抛：使持久化(doOnComplete)跳过、tracing(doOnError)标 ERROR。
            return Flux.<String>just(AgentStreamEvent.error("Markdown Agent 流式执行失败"))
                    .concatWith(Flux.error(new KbException(
                            "Markdown Agent 流式执行失败：" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR)));
        }
    }

    /**
     * 把 Agent 的 {@link NodeOutput} 流映射为过程事件 JSON 流：
     * <ul>
     *   <li>{@code AGENT_MODEL_STREAMING} 的 chunk 经 {@link ThinkStreamSplitter} 切成 thinking / answer 事件；</li>
     *   <li>{@code AGENT_MODEL_FINISHED} 的 {@code toolCalls} → tool_call 事件；</li>
     *   <li>{@code AGENT_TOOL_FINISHED} → 按序消费 {@code acc} 的工具调用记录 → tool_result 事件；</li>
     *   <li>流末尾补 citations + done；异常经 onErrorResume 转成脱敏 error 并重抛。</li>
     * </ul>
     */
    private Flux<String> mapToEventStream(Flux<NodeOutput> outputs, MdAccumulator acc) {
        ThinkStreamSplitter splitter = new ThinkStreamSplitter();
        AtomicInteger toolResultCursor = new AtomicInteger(0);
        return outputs
                .concatMap(output -> Flux.fromIterable(toEvents(output, splitter, acc, toolResultCursor)))
                .concatWith(Flux.defer(() -> Flux.fromIterable(splitter.flush())))
                .concatWith(Flux.defer(() -> Flux.just(AgentStreamEvent.citations(assembleCitations(acc)))))
                .concatWith(Flux.just(AgentStreamEvent.done()))
                // 发完 error 事件后「重抛」原异常：前端拿到结构化错误，同时让上游 doOnComplete 跳过持久化、
                // tracing doOnError 把根 span 标 ERROR。绝不能 onErrorResume 成正常完成——否则半截答案会被当成功入库。
                .onErrorResume(e -> {
                    log.error("Markdown Agentic 流式映射失败", e);
                    return Flux.<String>just(AgentStreamEvent.error("流式处理失败")).concatWith(Flux.error(e));
                });
    }

    private List<String> toEvents(NodeOutput output, ThinkStreamSplitter splitter,
                                  MdAccumulator acc, AtomicInteger toolResultCursor) {
        if (!(output instanceof StreamingOutput<?> so)) {
            return List.of();
        }
        OutputType type = so.getOutputType();
        if (type == OutputType.AGENT_MODEL_STREAMING) {
            String chunk = so.chunk();
            return (chunk == null || chunk.isEmpty()) ? List.of() : splitter.accept(chunk);
        }
        if (type == OutputType.AGENT_MODEL_FINISHED) {
            return toolCallEvents(so.message());
        }
        if (type == OutputType.AGENT_TOOL_FINISHED) {
            return toolResultEvents(acc, toolResultCursor);
        }
        return List.of();
    }

    /** 模型决策轮结束：把 {@code toolCalls} 映射为 tool_call 事件（关键词 / parentId 暴露给前端）。 */
    private List<String> toolCallEvents(Message message) {
        if (!(message instanceof AssistantMessage am) || !am.hasToolCalls()) {
            return List.of();
        }
        List<String> events = new ArrayList<>();
        for (AssistantMessage.ToolCall toolCall : am.getToolCalls()) {
            Map<String, Object> event = AgentStreamEvent.event("tool_call");
            event.put("tool", toolCall.name());
            if ("readFullSection".equals(toolCall.name())) {
                event.put("parentId", extractToolArg(toolCall.name(), toolCall.arguments()));
            } else {
                event.put("query", extractToolArg(toolCall.name(), toolCall.arguments()));
            }
            events.add(AgentStreamEvent.json(event));
        }
        return events;
    }

    /** 工具执行结束：按序消费工具调用记录（含早退分支），暴露命中数 / 状态。 */
    private List<String> toolResultEvents(MdAccumulator acc, AtomicInteger toolResultCursor) {
        List<MdAccumulator.ToolCallTrace> snapshot;
        synchronized (acc.toolCallTraces) {
            snapshot = new ArrayList<>(acc.toolCallTraces);
        }
        List<String> events = new ArrayList<>();
        for (int i = toolResultCursor.get(); i < snapshot.size(); i++) {
            MdAccumulator.ToolCallTrace trace = snapshot.get(i);
            Map<String, Object> event = AgentStreamEvent.event("tool_result");
            event.put("tool", trace.tool());
            event.put("status", trace.status());
            if ("searchKnowledgeBase".equals(trace.tool())) {
                event.put("hits", trace.hits());
            } else {
                event.put("ok", "ok".equals(trace.status()));
            }
            events.add(AgentStreamEvent.json(event));
        }
        toolResultCursor.set(snapshot.size());
        List<Citation> citations = assembleCitations(acc);
        if (!citations.isEmpty()) {
            events.add(AgentStreamEvent.citations(citations));
        }
        return events;
    }

    private String extractToolArg(String toolName, String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(argumentsJson);
            String key = "readFullSection".equals(toolName) ? "parentId" : "query";
            JsonNode value = node.get(key);
            return value != null ? value.asText("") : "";
        } catch (Exception e) {
            return "";
        }
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
            acc.recordSearch("max_tool_calls", 0);
            return "已达到最大工具调用次数，请根据已有信息作答。";
        }
        if (!acc.searchedQueries.add(query)) {
            acc.recordSearch("duplicate_query", 0);
            return "该关键词已检索过，请换用不同关键词或根据已有内容作答。";
        }
        List<SearchHit> candidates = hybridSearchService.search(spaceId,
                new SearchRequest(query, TOOL_RECALL_SIZE, null, null, query)).hits();
        List<SearchHit> hits = rerankService.rerank(query, candidates, TOOL_RERANK_TOP_N);
        if (hits.isEmpty()) {
            acc.recordSearch("no_hits", 0);
            return "未找到相关 Markdown child 片段。";
        }
        acc.recordSearch("ok", hits.size());
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
            acc.recordRead("max_tool_calls");
            return "已达到最大工具调用次数，请根据已有信息作答。";
        }
        UUID id;
        try {
            id = UUID.fromString(parentId);
        } catch (IllegalArgumentException e) {
            acc.recordRead("invalid_parent_id");
            return "parentId 格式不正确。";
        }
        if (!acc.expandedParents.add(id)) {
            acc.recordRead("duplicate_parent");
            return "该 parentId 已读取过，请根据已有 section 内容作答。";
        }
        MdParentChunk parent = parentChunkMapper.findById(id);
        if (parent == null || !spaceId.equals(parent.getSpaceId())) {
            acc.recordRead("not_found");
            return "未找到该 section，或 section 不属于当前空间。";
        }
        acc.recordRead("ok");
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

    private List<Citation> assembleCitations(MdAccumulator acc) {
        List<SearchHit> orderedHits;
        synchronized (acc.parentHitsById) {
            orderedHits = new ArrayList<>(acc.parentHitsById.values());
        }
        if (orderedHits.isEmpty()) {
            synchronized (acc.childHitsByParent) {
                orderedHits = new ArrayList<>(acc.childHitsByParent.values());
            }
        }
        return CitationAssembler.fromHits(orderedHits);
    }

    /** 在 Agent 系统 prompt 前拼接画像软默认块（空则不拼，行为与未引入画像时一致）。 */
    private String composeSystemPrompt(String profileBlock) {
        String base = systemPrompt();
        return profileBlock == null || profileBlock.isBlank() ? base : profileBlock + "\n" + base;
    }

    private String systemPrompt() {
        return promptProvider.renderForTrace(AGENTIC_SYSTEM_PROMPT, Map.of("image_context_rule", IMAGE_CONTEXT_RULE));
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLen) + "\n\n[section 内容过长，已截断]";
    }

    static final class MdAccumulator {
        /** 本轮 Agent 已执行的工具调用次数，用于限制 ReAct 循环中工具调用上限。 */
        final AtomicInteger toolCallCount = new AtomicInteger(0);
        /** 本轮已检索过的关键词，防止模型重复调用 searchKnowledgeBase 查询同一关键词。 */
        final Set<String> searchedQueries = new HashSet<>();
        /** 本轮已读取过的 parent section ID，防止模型重复调用 readFullSection 展开同一 section。 */
        final Set<UUID> expandedParents = new HashSet<>();
        /** child 检索命中的代表片段，按 parentId 去重保序；同步响应用它兜底组装 citation。 */
        final Map<UUID, SearchHit> childHitsByParent = Collections.synchronizedMap(new LinkedHashMap<>());
        /** 已展开 parent section 的完整命中，按读取顺序保序；优先用于组装最终 citation。 */
        final Map<UUID, SearchHit> parentHitsById = Collections.synchronizedMap(new LinkedHashMap<>());
        /**
         * 工具调用记录（含早退分支），供流映射按序生成 tool_result 事件。
         * 每次工具调用无论成功 / 无命中 / 早退都恰好追加一条，保证与 {@code AGENT_TOOL_FINISHED} 对齐。
         * graph 执行线程与流映射线程可能不同，故用同步 List。
         */
        final List<ToolCallTrace> toolCallTraces = Collections.synchronizedList(new ArrayList<>());

        void recordSearch(String status, int hits) {
            toolCallTraces.add(new ToolCallTrace("searchKnowledgeBase", status, hits));
        }

        void recordRead(String status) {
            toolCallTraces.add(new ToolCallTrace("readFullSection", status, 0));
        }

        /**
         * 单次工具调用的结果记录。
         *
         * @param tool   工具名
         * @param status 状态（ok / no_hits / duplicate_query / max_tool_calls / invalid_parent_id / duplicate_parent / not_found）
         * @param hits   命中数（仅 searchKnowledgeBase 有意义）
         */
        record ToolCallTrace(String tool, String status, int hits) {
        }
    }

    /**
     * 流式 thinking/answer 分流器（兜底路径 2）：把模型 content 增量按 {@code <think>...</think>} 边界
     * 切成 thinking 与 answer 事件。标签内 → thinking，标签外 → answer；标签可能被 llama.cpp 流式切断，
     * 故用 {@code buf} 保留「可能是半个标签」的尾巴，跨 chunk 拼接后再判定。
     *
     * <p>前提：llama-server 以 {@code --reasoning-format none} 运行，使 {@code <think>} 内联进 content；
     * 若用 {@code deepseek} 格式（reasoning 走独立 reasoning_content 字段），则 content 无标签，
     * 全部按 answer 输出（thinking 不展示），见设计文档 §6。</p>
     */
    static final class ThinkStreamSplitter {

        private static final String OPEN = "<think>";
        private static final String CLOSE = "</think>";

        private final StringBuilder buf = new StringBuilder();
        private boolean insideThink = false;

        List<String> accept(String chunk) {
            buf.append(chunk);
            return drain(false);
        }

        List<String> flush() {
            return drain(true);
        }

        private List<String> drain(boolean end) {
            List<String> events = new ArrayList<>();
            while (true) {
                String tag = insideThink ? CLOSE : OPEN;
                int idx = buf.indexOf(tag);
                if (idx >= 0) {
                    emit(events, buf.substring(0, idx));
                    buf.delete(0, idx + tag.length());
                    insideThink = !insideThink;
                    continue;
                }
                // 无完整标签：发出「安全前缀」，保留可能是半个标签的尾巴（流未结束时）。
                int keep = end ? 0 : partialTailLen(buf, tag);
                int emitLen = buf.length() - keep;
                if (emitLen > 0) {
                    emit(events, buf.substring(0, emitLen));
                    buf.delete(0, emitLen);
                }
                if (end && buf.length() > 0) {
                    emit(events, buf.toString());
                    buf.setLength(0);
                }
                break;
            }
            return events;
        }

        private void emit(List<String> events, String text) {
            if (text.isEmpty()) {
                return;
            }
            if (insideThink && text.isBlank()) {
                return;
            }
            events.add(insideThink ? AgentStreamEvent.thinking(text) : AgentStreamEvent.answer(text));
        }

        /** buf 末尾最长的、同时是 tag 真前缀的后缀长度（用于跨 chunk 保留半个标签）。 */
        private static int partialTailLen(StringBuilder buf, String tag) {
            int max = Math.min(buf.length(), tag.length() - 1);
            for (int len = max; len > 0; len--) {
                if (tag.startsWith(buf.substring(buf.length() - len))) {
                    return len;
                }
            }
            return 0;
        }
    }
}
