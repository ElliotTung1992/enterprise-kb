package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.Domain;
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

    /** 路由调用使用的模型提供商；路由是轻量分类，用便宜快模型即可。 */
    @Value("${enterprise.kb.ai.router-provider:LLAMA_CPP}")
    private String routerProvider;

    /** 喂给路由器的上下文轮数，按评估集校准。 */
    @Value("${enterprise.kb.ai.router-context-turns:3}")
    private int contextTurns;

    private final ModelProviderResolver modelProviderResolver;

    @Override
    public RoutingDecision route(List<Message> history, ConversationState state, String message) {
        if (message == null || message.isBlank()) {
            return RoutingDecision.of(Domain.UNCLEAR, null, "用户消息为空");
        }
        try {
            ChatClient chatClient = modelProviderResolver.resolveChatClient(routerProvider);
            String raw = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
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

    // ---- Router system prompt（域目录 + 规则 + few-shot；few-shot 为内联手写，不与冻结测试集重叠） ----

    private static final String SYSTEM_PROMPT = """
            你是商城客服系统的意图路由器。任务：把"用户最新消息"分类到一个业务域。
            你只负责判断"哪个域"，不负责回答用户、不负责选择具体工具。

            【业务域目录】
            - AFTER_SALES（售后域）：普通退款、换货、维修等售后咨询与申请。
            - COMPLAINT（投诉域）：复杂投诉升级。命中任一即属此域——明确要求"正式投诉/升级处理/找上级"；
              表示此前已多次反映未解决；涉及商家+物流+平台多方责任纠纷；情绪激烈且伴随明确诉求。
            - HANDOFF（转人工）：属于商城业务范畴、但当前系统尚未接入对应域。例如物流查询、
              账户与会员、商品咨询、营销活动等——这些是真实业务，必须转人工，不可当闲聊拒绝。
            - CHITCHAT（域外闲聊）：与商城业务无关的内容，如天气、闲聊、问"你是不是机器人"。
            - UNCLEAR（无法判定）：信息不足，无法判断属于哪个业务域。

            【判定规则】
            1. 证据优先：必须能从用户原话摘出指向某个域的具体证据。摘不出 → 输出 UNCLEAR。
            2. 情绪 ≠ 切换信号：单纯的情绪宣泄（"你们服务真差"）不构成投诉域证据；
               只有"情绪 + 明确诉求"才进 COMPLAINT。纯情绪且无上下文 → UNCLEAR。
            3. 跟随当前域：若有"当前所处业务域"且最新消息是对该域的补充/延续，维持该域。
            4. 意图漂移：用户出现新的、明确的其他域诉求时，切换到新域。
            5. 复合意图：一句话含多个域诉求时，主域取诉求最明确者（投诉域优先于售后域），
               其余真实业务域填入次要域。
            6. 待确认提议：若状态中存在"待确认提议域"，且用户最新消息是对该提议的肯定/接受回应
               （如"是""好""可以""麻烦你了"），输出该提议域；若用户否定或转向新问题，
               忽略提议、按新消息正常判定。
            7. 等待槽位回填：若状态中"等待槽位回填=是"，且用户最新消息是对该槽位的补充
               （如包含订单号、电话、或类似"我的订单号是 SO123""就是上次那个订单"），维持当前域；
               若用户明确放弃原诉求并表达新诉求（如"算了不退了""我要直接投诉""换个问题"），
               按"意图漂移"规则切换到新域。

            【输出格式】
            只输出一行，不要任何解释，格式为五段以竖线分隔：
            PRIMARY|SECONDARY_CSV|RUNNER_UP|EVIDENCE|EMOTION
            - PRIMARY：主域，取 AFTER_SALES / COMPLAINT / HANDOFF / CHITCHAT / UNCLEAR 之一
            - SECONDARY_CSV：次要业务域，逗号分隔；无则填 NONE
            - RUNNER_UP：次可能的域；无则填 NONE
            - EVIDENCE：判定主域所依据的用户原话证据（一句中文）
            - EMOTION：用户最新消息是否带明显负面情绪宣泄（愤怒/失望/讽刺），取 EMOTIONAL 或 NEUTRAL

            【示例】
            用户：我要退款 → AFTER_SALES|NONE|NONE|用户明确提出退款诉求|NEUTRAL
            用户：这个怎么换货啊 → AFTER_SALES|NONE|NONE|用户咨询换货流程|NEUTRAL
            用户：我已经投诉两次了一直没人管 → COMPLAINT|NONE|AFTER_SALES|用户表示多次反映未解决|EMOTIONAL
            用户：我要正式投诉你们 → COMPLAINT|NONE|NONE|用户明确要求正式投诉|NEUTRAL
            用户：你们物流烂透了，我要投诉商家发错货 → COMPLAINT|NONE|NONE|情绪伴随明确投诉诉求|EMOTIONAL
            用户：你们服务真差劲（无当前域）→ UNCLEAR|NONE|COMPLAINT|纯情绪宣泄，无具体诉求|EMOTIONAL
            用户：你们效率太低了（当前域=AFTER_SALES）→ AFTER_SALES|NONE|NONE|情绪不构成切域信号，维持售后域|EMOTIONAL
            用户：今天天气怎么样 → CHITCHAT|NONE|NONE|与商城业务无关的闲聊|NEUTRAL
            用户：你是机器人吗 → CHITCHAT|NONE|NONE|与业务无关的寒暄|NEUTRAL
            用户：帮我查下我的快递到哪了 → HANDOFF|NONE|NONE|物流查询，对应域尚未接入|NEUTRAL
            用户：我要改一下会员手机号 → HANDOFF|NONE|NONE|账户会员业务，对应域尚未接入|NEUTRAL
            用户：我要退款，另外想正式投诉商家态度 → AFTER_SALES|COMPLAINT|NONE|含退款与投诉两个诉求|NEUTRAL
            用户：算了别退款了，我要直接投诉你们（当前域=AFTER_SALES）→ COMPLAINT|NONE|AFTER_SALES|用户放弃退款转为明确投诉诉求|NEUTRAL
            用户：我的订单号是 SO20260520001（当前域=AFTER_SALES，等待槽位回填=是）→ AFTER_SALES|NONE|NONE|用户补充售后所需订单号，维持当前域|NEUTRAL
            用户：算了不退了，我要直接投诉你们处理太慢（当前域=AFTER_SALES，等待槽位回填=是）→ COMPLAINT|NONE|AFTER_SALES|用户放弃退款明确转为投诉诉求，按意图漂移切域|NEUTRAL
            用户：在吗 → UNCLEAR|NONE|NONE|信息不足，无法判定业务域|NEUTRAL
            用户：是的，麻烦你了（待确认提议域=COMPLAINT）→ COMPLAINT|NONE|NONE|用户接受升级投诉的提议|NEUTRAL
            """;
}
