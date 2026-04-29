package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.service.AgenticTokenBudgetService;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link AgenticTokenBudgetService} 实现。
 *
 * <p>计算逻辑（token 为单位）：
 * <pre>
 * available      = contextLimit - systemTokens - toolSchemaTokens - questionTokens - SAFETY_BUFFER
 * historyBudget  = min(actualHistoryTokens, available × MAX_HISTORY_RATIO)
 * retrievalSpace = available - historyBudget
 * </pre>
 *
 * <p>配置项（单位均为 token）：
 * <ul>
 *   <li>{@code enterprise.kb.ai.context-limit} — 模型 context window 上限，默认 204800</li>
 *   <li>{@code enterprise.kb.ai.agentic.chunk-token-size} — 每个 chunk 的 token 上限，默认 512</li>
 * </ul>
 */
@Slf4j
@Service
public class AgenticTokenBudgetServiceImpl implements AgenticTokenBudgetService {

    @Value("${enterprise.kb.ai.context-limit:204800}")
    private int contextLimit;

    @Value("${enterprise.kb.ai.agentic.chunk-token-size:512}")
    private int chunkTokenSize;

    /** 安全缓冲，防止计算误差导致超限 */
    private static final int SAFETY_BUFFER = 2000;
    /** history 占可用空间的最大比例上限 */
    private static final double MAX_HISTORY_RATIO = 0.4;
    /** 压缩时原样保留的最新消息条数 */
    private static final int MIN_RECENT_MESSAGES = 4;

    /** Agentic System Prompt 的 token 数（启动时测一次） */
    private int systemPromptTokens;

    /** Tool schema 的 token 数（启动时测一次） */
    private int toolSchemaTokens;

    private final ModelProviderResolver modelProviderResolver;
    private final Encoding encoding;

    public AgenticTokenBudgetServiceImpl(ModelProviderResolver modelProviderResolver) {
        this.modelProviderResolver = modelProviderResolver;
        this.encoding = com.knuddels.jtokkit.Encodings.newLazyEncodingRegistry()
                .getEncoding(EncodingType.CL100K_BASE);
    }

    @PostConstruct
    public void init() {
        systemPromptTokens = count(AgenticTokenBudgetService.AGENT_SYSTEM_PROMPT);
        toolSchemaTokens = count(AgenticTokenBudgetService.TOOL_SCHEMA);
        log.info("Agentic token budget initialized: contextLimit={}, systemPromptTokens={}, toolSchemaTokens={}, maxHistoryRatio={}",
                contextLimit, systemPromptTokens, toolSchemaTokens, MAX_HISTORY_RATIO);
    }

    @Override
    public Budget compute(String question, List<Message> history) {
        int questionTokens = count(question);
        int available = Math.max(0, contextLimit - systemPromptTokens - toolSchemaTokens
                - questionTokens - SAFETY_BUFFER);

        int actualHistoryTokens = history.stream().mapToInt(m -> count(m.getText())).sum();
        int historyCap = (int)(available * MAX_HISTORY_RATIO);
        int historyBudget = Math.min(actualHistoryTokens, historyCap);
        int retrievalSpace = available - historyBudget;

        log.debug("Token budget: questionTokens={}, available={}, actualHistory={}, historyBudget={}, retrievalSpace={}",
                questionTokens, available, actualHistoryTokens, historyBudget, retrievalSpace);

        return new Budget(historyBudget, retrievalSpace);
    }

    @Override
    public List<Message> compressHistory(List<Message> history, int maxTokens) {
        if (history.isEmpty()) return Collections.emptyList();

        int totalTokens = history.stream().mapToInt(m -> count(m.getText())).sum();
        if (totalTokens <= maxTokens) return new ArrayList<>(history);

        // 最新 MIN_RECENT_MESSAGES 条原样保留，其余 LLM 摘要压缩为一条 SystemMessage
        int recentCount = Math.min(MIN_RECENT_MESSAGES, history.size() - 1);
        List<Message> recentMessages = new ArrayList<>(
                history.subList(history.size() - recentCount, history.size()));
        List<Message> toSummarize = history.subList(0, history.size() - recentCount);

        String summary = summarize(toSummarize);

        List<Message> result = new ArrayList<>(recentCount + 1);
        result.add(new SystemMessage("以下是历史对话的摘要：\n" + summary));
        result.addAll(recentMessages);
        log.debug("Compress history: original={} msgs, summarized={} msgs, kept recent={} msgs",
                history.size(), toSummarize.size(), recentCount);
        return result;
    }

    private String summarize(List<Message> messages) {
        StringBuilder dialogue = new StringBuilder();
        for (Message msg : messages) {
            String role = (msg instanceof UserMessage) ? "用户" : "助手";
            dialogue.append(role).append("：").append(msg.getText()).append("\n\n");
        }
        try {
            return modelProviderResolver.resolveChatClient(null)
                    .prompt()
                    .system("你是对话摘要助手。请将以下对话历史压缩为简洁的摘要，保留关键信息和用户意图，摘要不超过 300 字。")
                    .user(dialogue.toString())
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("历史摘要生成失败，fallback 为文本截断", e);
            String raw = dialogue.toString();
            return raw.length() > 600 ? raw.substring(0, 600) + "…" : raw;
        }
    }

    private int count(String text) {
        if (text == null || text.isBlank()) return 0;
        return encoding.encode(text).size();
    }

}