package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.Domain;
import com.enterprise.kb.common.prompt.PromptProvider;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.dto.ConversationState;
import com.enterprise.kb.search.dto.RoutingDecision;
import com.enterprise.kb.search.service.DomainRouterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tier-1 域路由服务实现。
 *
 * <p>用一次轻量模型调用完成域分类。LLM 被要求输出单行管道格式
 * {@code PRIMARY|SECONDARY_CSV|RUNNER_UP|EVIDENCE|EMOTION}，与本项目既有的 LLM
 * 结构化输出风格（见 {@code ComplaintResponsibilityInferenceServiceImpl}）一致。
 *
 * <p>任何解析失败或调用异常都降级为 {@code UNCLEAR}：误判域会让子 Agent 拿错工具集，
 * 代价远高于多反问一轮。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DomainRouterServiceImpl implements DomainRouterService {

    private static final String ROUTER_PROMPT = "kb/router/domain";

    /** 路由调用使用的模型提供商；路由是轻量分类，用便宜快模型即可。 */
    @Value("${enterprise.kb.ai.router-provider:LLAMA_CPP}")
    private String routerProvider;

    /** 喂给路由器的上下文轮数，按评估集校准。 */
    @Value("${enterprise.kb.ai.router-context-turns:3}")
    private int contextTurns;

    private final ModelProviderResolver modelProviderResolver;
    private final PromptProvider promptProvider;

    @Override
    public RoutingDecision route(List<Message> history, ConversationState state, String message) {
        if (message == null || message.isBlank()) {
            return RoutingDecision.of(Domain.UNCLEAR, null, "用户消息为空");
        }
        try {
            ChatClient chatClient = modelProviderResolver.resolveChatClient(routerProvider);
            String raw = chatClient.prompt()
                    .system(systemPrompt())
                    .user(buildUserPrompt(history, state, message))
                    .call()
                    .content();
            RoutingDecision decision = parse(raw);
            // 详细字段（含原始 emotional/runnerUp）用 DEBUG 输出，便于排查；
            // 调用方 CustomerAssistantServiceImpl 会在 INFO 级别打带 sessionId 的"Tier-1 路由判定"汇总。
            log.debug("域路由判定：primary={}，secondary={}，runnerUp={}，emotional={}，evidence={}",
                    decision.primaryDomain(), decision.secondary(), decision.runnerUp(),
                    decision.emotional(), decision.evidence());
            return decision;
        } catch (Exception e) {
            log.warn("域路由调用失败，降级为 UNCLEAR：{}", e.getMessage());
            return RoutingDecision.of(Domain.UNCLEAR, null, "路由调用异常，转人工澄清");
        }
    }

    // ---- Prompt 构建 ----

    private String systemPrompt() {
        return promptProvider.render(ROUTER_PROMPT, Map.of());
    }

    private String buildUserPrompt(List<Message> history, ConversationState state, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("【当前对话状态】\n");
        Domain currentDomain = state != null ? state.currentDomain() : null;
        sb.append("当前所处业务域：")
                .append(currentDomain != null ? currentDomain.name() : "无（新对话或尚未路由）")
                .append("\n");
        if (state != null && state.awaitingSlot() && currentDomain != null) {
            sb.append("等待槽位回填：是（上一轮已向用户索要订单号等槽位；")
                    .append("本轮可能是槽位回填，也可能是意图漂移，请根据消息内容判定）\n");
        }
        Domain pendingOffer = state != null ? state.pendingOffer() : null;
        if (pendingOffer != null) {
            sb.append("待确认提议域：").append(pendingOffer.name())
                    .append("（上一轮已反问用户是否需要处理，本轮在等用户回应）\n");
        }
        sb.append("\n");

        sb.append("【最近对话】\n");
        List<Message> recent = recentTurns(history);
        if (recent.isEmpty()) {
            sb.append("（无历史）\n");
        } else {
            for (Message m : recent) {
                String role = m instanceof AssistantMessage ? "客服" : "用户";
                String text = m.getText() != null ? m.getText() : "";
                sb.append(role).append("：").append(truncate(text)).append("\n");
            }
        }

        sb.append("\n【用户最新消息】\n").append(message).append("\n\n");
        sb.append("请按规定格式输出一行路由判定。");
        return sb.toString();
    }

    /** 取最近 contextTurns 轮（每轮 user+assistant 各 1 条，故取末尾 2*contextTurns 条）。 */
    private List<Message> recentTurns(List<Message> history) {
        if (CollectionUtils.isEmpty(history)) {
            return List.of();
        }
        int window = Math.max(0, contextTurns) * 2;
        return history.size() > window
                ? history.subList(history.size() - window, history.size())
                : history;
    }

    private String truncate(String text) {
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }

    // ---- 响应解析 ----

    /**
     * 解析 {@code PRIMARY|SECONDARY_CSV|RUNNER_UP|EVIDENCE|EMOTION} 单行格式。
     * 防御性处理：扫描所有行，取第一行能解析出合法主域的行；缺少的尾段按缺省值处理。
     */
    private RoutingDecision parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return RoutingDecision.of(Domain.UNCLEAR, null, "路由器无输出");
        }
        for (String line : raw.strip().split("\\R")) {
            String trimmed = line.strip();
            if (!trimmed.contains("|")) {
                continue;
            }
            String[] parts = trimmed.split("\\|", 5);
            Domain primary = parseDomain(parts[0]);
            if (primary == null) {
                continue;
            }
            List<Domain> secondary = parts.length > 1 ? parseDomainList(parts[1]) : List.of();
            Domain runnerUp = parts.length > 2 ? parseDomain(parts[2]) : null;
            String evidence = parts.length > 3 ? parts[3].strip() : "";
            boolean emotional = parts.length > 4 && "EMOTIONAL".equalsIgnoreCase(parts[4].strip());
            return new RoutingDecision(primary, secondary, runnerUp, evidence, emotional);
        }
        log.warn("路由器输出无法解析，降级为 UNCLEAR：{}", truncate(raw));
        return RoutingDecision.of(Domain.UNCLEAR, null, "路由器输出格式异常");
    }

    private Domain parseDomain(String token) {
        if (token == null) {
            return null;
        }
        String t = token.strip().toUpperCase();
        if (t.isEmpty() || "NONE".equals(t)) {
            return null;
        }
        try {
            return Domain.valueOf(t);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private List<Domain> parseDomainList(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<Domain> domains = new ArrayList<>();
        for (String token : csv.split(",")) {
            Domain d = parseDomain(token);
            // 次要域只保留真实业务域，过滤掉 NONE / HANDOFF / CHITCHAT / UNCLEAR 噪声
            if (d != null && d.isBusinessDomain()) {
                domains.add(d);
            }
        }
        return domains;
    }

}
