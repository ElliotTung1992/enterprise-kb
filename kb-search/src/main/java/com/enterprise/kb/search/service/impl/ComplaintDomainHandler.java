package com.enterprise.kb.search.service.impl;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.enterprise.kb.common.constants.Domain;
import com.enterprise.kb.common.exception.KbException;
import com.enterprise.kb.common.prompt.PromptProvider;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.dto.DomainContext;
import com.enterprise.kb.search.dto.DomainResult;
import com.enterprise.kb.search.service.ComplaintEscalationService;
import com.enterprise.kb.search.service.ComplaintWorkflowService;
import com.enterprise.kb.search.service.DomainHandler;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * 投诉域处理器（Tier-2）。
 *
 * <p>用一个只带 {@code escalateComplaint} 工具的 ReactAgent 收集投诉内容与关联订单号，
 * 信息齐全后触发投诉升级 StateGraph（见 ADR-007）。StateGraph 本身不改动，
 * 本处理器只是它在两层路由架构下的域入口包装。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintDomainHandler implements DomainHandler {

    private static final String COMPLAINT_HANDLER_PROMPT = "kb/complaint/handler";
    private static final int MAX_TOOL_CALLS = 5;
    private static final int MAX_RECURSION_LIMIT = MAX_TOOL_CALLS * 2 + 1;

    private final ModelProviderResolver modelProviderResolver;
    private final RedisSaver agentCheckpointSaver;
    private final ComplaintEscalationService complaintEscalationService;
    private final ComplaintWorkflowService complaintWorkflowService;
    private final PromptProvider promptProvider;

    @Override
    public Domain domain() {
        return Domain.COMPLAINT;
    }

    /**
     * 伪造案件编号检测：{@code escalateComplaint} 工具的返回字符串里必然带
     * {@code "案件编号：<UUID>"} 这一段。Agent 没调工具就不可能合法拿到案件编号，
     * 因此回复里出现"案件编号 + 标识符"是工具未调时的**硬伪造**信号。
     *
     * <p>放弃用文本启发猜"是不是宣称完成"——因为同一句话（如"稍后专员跟进处理"）
     * 在收集信息阶段和工具返回后语境意思相反，regex 无法区分。改成只抓"只能由工具
     * 产生的产物"，配合 prompt 把 Agent 锁进"想宣称已升级必须报案件编号"的两难策略：
     * 想编 → 命中本 pattern 被拦截；不编 → 违反 prompt，被另一道 prompt 兜底。</p>
     */
    private static final Pattern FABRICATED_CASE_ID_PATTERN = Pattern.compile(
            "案件编号\\s*[：:]\\s*[A-Za-z0-9][A-Za-z0-9\\-]{3,}");

    /** Agent 伪造案件编号但实际没调工具的兜底话术。 */
    private static final String FALLBACK_NO_TOOL_INVOCATION =
            "抱歉，刚才的升级流程没有真正提交到系统（系统里没有对应案件记录）。"
                    + "麻烦您回复「重新提交投诉」或直接补充投诉详情，我会立刻为您正式登记并返回真实案件编号。";

    @Override
    public DomainResult handle(DomainContext ctx) {
        AtomicBoolean toolInvoked = new AtomicBoolean(false);
        ReactAgent agent = buildAgent(ctx, toolInvoked);

        List<Message> messages = new ArrayList<>(ctx.history());
        messages.add(new UserMessage(ctx.message()));

        RunnableConfig config = RunnableConfig.builder()
                .threadId(ctx.sessionId() + ":complaint")
                .build();

        try {
            AssistantMessage assistantMessage = agent.call(messages, config);
            String answer = assistantMessage.getText();
            // 幻觉拦截：未调 escalateComplaint 却给出案件编号 → 必为伪造（编号只可能来自工具返回值）
            if (!toolInvoked.get() && answer != null
                    && FABRICATED_CASE_ID_PATTERN.matcher(answer).find()) {
                log.warn("【投诉幻觉拦截】Agent 未调用 escalateComplaint 却伪造案件编号：sessionId={}，answer={}",
                        ctx.sessionId(), truncate(answer));
                return DomainResult.of(FALLBACK_NO_TOOL_INVOCATION);
            }
            return DomainResult.of(answer);
        } catch (Exception e) {
            log.error("投诉域处理失败：sessionId={}", ctx.sessionId(), e);
            throw new KbException("对话执行失败：" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }

    // ---- Agent 构建 ----

    /**
     * 构建带"工具调用追踪"的 Agent。tool callback 真实进入时把 {@code toolInvoked} 置 true，
     * 供上层判断 Agent 是否走了正确流程。
     *
     * <p>package-private + 接收 {@code toolInvoked}：作为单测 seam，子类覆写时可手动控制
     * 该标记以模拟"调过工具 / 没调工具"两种场景。</p>
     */
    ReactAgent buildAgent(DomainContext ctx, AtomicBoolean toolInvoked) {
        FunctionToolCallback<EscalateComplaintInput, String> escalateTool = FunctionToolCallback.builder(
                        "escalateComplaint",
                        (EscalateComplaintInput input) -> {
                            toolInvoked.set(true);
                            return escalateComplaint(ctx, input);
                        })
                .description("""
                        将用户投诉升级为正式投诉案件并自动生成 AI 处理计划，提交专员审核。
                        参数：orderId（关联订单号）、description（用户投诉详细描述，需包含问题背景和诉求）。
                        信息齐全后调用；调用后系统自动认定责任方并生成处理计划，结果将通知用户。
                        """)
                .inputType(EscalateComplaintInput.class)
                .build();

        ChatClient chatClient = modelProviderResolver.resolveChatClient(ctx.modelProvider());
        return ReactAgent.builder()
                .name("complaint-domain-agent")
                .chatClient(chatClient)
                .systemPrompt(promptProvider.render(COMPLAINT_HANDLER_PROMPT, Map.of()))
                .tools(escalateTool)
                .compileConfig(CompileConfig.builder()
                        .saverConfig(SaverConfig.builder().register(agentCheckpointSaver).build())
                        .recursionLimit(MAX_RECURSION_LIMIT)
                        .build())
                .build();
    }

    // ---- 投诉升级 ----

    // package-private：投诉升级工具回调，供单测直接验证校验与服务编排
    String escalateComplaint(DomainContext ctx, EscalateComplaintInput input) {
        if (input.orderId() == null || input.orderId().isBlank()) {
            return "请提供关联订单号后再提交升级投诉。";
        }
        if (input.description() == null || input.description().isBlank()) {
            return "请描述具体投诉内容后再提交。";
        }
        try {
            var complaint = complaintEscalationService.createComplaint(
                    ctx.userId(), input.orderId(), input.description());
            complaintWorkflowService.startPlanning(complaint.getId());
            log.info("投诉升级已提交：complaintId={}，orderId={}，userId={}",
                    complaint.getId(), input.orderId(), ctx.userId());
            return "您的投诉已成功升级，案件编号：" + complaint.getId()
                    + "。AI 已完成责任认定并生成处理计划，专员审核通过后将立即跟进处理，结果将以通知方式告知您。";
        } catch (Exception e) {
            log.error("投诉升级失败：userId={}，orderId={}", ctx.userId(), input.orderId(), e);
            return "投诉升级提交失败，请稍后重试或联系人工客服。";
        }
    }

    record EscalateComplaintInput(String orderId, String description) {}

}
