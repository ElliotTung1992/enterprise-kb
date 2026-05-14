package com.enterprise.kb.search.service.impl;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.enterprise.kb.common.constants.ComplaintPlanStatus;
import com.enterprise.kb.common.constants.ComplaintStatus;
import com.enterprise.kb.common.exception.KbException;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.dto.ComplaintExecutionResult;
import com.enterprise.kb.search.model.ComplaintPlan;
import com.enterprise.kb.search.service.ComplaintEscalationService;
import com.enterprise.kb.search.service.ComplaintExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 投诉升级处理计划执行服务实现。
 * <p>使用 ReactAgent 驱动工具调用，按 contactSequence 顺序通知责任方、执行补偿、记录结案。
 * 所有外部系统调用均为 mock，待接入真实系统时替换工具实现即可。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintExecutorServiceImpl implements ComplaintExecutorService {

    private static final int MAX_RECURSION_LIMIT = 15;

    private final ComplaintEscalationService complaintEscalationService;
    private final ModelProviderResolver modelProviderResolver;

    /**
     * {@inheritDoc}
     */
    @Override
    public ComplaintExecutionResult execute(UUID planId) {
        ComplaintPlan plan = complaintEscalationService.findPlanById(planId);

        if (plan.getStatus() != ComplaintPlanStatus.APPROVED) {
            throw new KbException(
                    "只有审批通过（APPROVED）的计划才能执行，当前状态：" + plan.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        complaintEscalationService.startPlanExecution(planId, Instant.now().plus(48, ChronoUnit.HOURS));
        log.info("开始执行投诉处理计划：planId={}，complaintId={}", planId, plan.getComplaintId());

        try {
            String summary = runAgent(plan);
            complaintEscalationService.updatePlanStatus(planId, ComplaintPlanStatus.COMPLETED);
            complaintEscalationService.updateComplaintStatus(plan.getComplaintId(), ComplaintStatus.RESOLVED);
            log.info("投诉处理计划执行完成：planId={}", planId);
            return new ComplaintExecutionResult(planId, plan.getComplaintId(), summary);
        } catch (GraphRunnerException | KbException e) {
            complaintEscalationService.updatePlanStatus(planId, ComplaintPlanStatus.FAILED);
            log.error("投诉处理计划执行失败：planId={}", planId, e);
            throw new KbException("计划执行失败：" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String runAgent(ComplaintPlan plan) throws GraphRunnerException {
        ChatClient chatClient = modelProviderResolver.resolveChatClient(null);
        AtomicBoolean resolutionRecorded = new AtomicBoolean(false);
        ReactAgent agent = buildAgent(plan, chatClient, resolutionRecorded);

        String planMessage = buildPlanMessage(plan);
        RunnableConfig config = RunnableConfig.builder()
                .threadId("executor-" + plan.getId())
                .build();

        var result = agent.call(List.of(new UserMessage(planMessage)), config);

        if (!resolutionRecorded.get()) {
            throw new KbException("ReactAgent 未调用 recordResolution，执行未完成", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return result.getText();
    }

    private ReactAgent buildAgent(ComplaintPlan plan, ChatClient chatClient, AtomicBoolean resolutionRecorded) {
        UUID complaintId = plan.getComplaintId();

        FunctionToolCallback<NotifyPartyInput, String> notifyTool = FunctionToolCallback.builder(
                        "notifyParty",
                        (NotifyPartyInput input) -> mockNotifyParty(input))
                .description("""
                        通知责任方（商家/物流/平台）处理投诉。
                        参数：partyType（MERCHANT/LOGISTICS/PLATFORM）、caseId、deadline（如 48h）。
                        返回：通知结果。
                        """)
                .inputType(NotifyPartyInput.class)
                .build();

        FunctionToolCallback<ExecuteCompensationInput, String> compensationTool = FunctionToolCallback.builder(
                        "executeCompensation",
                        (ExecuteCompensationInput input) -> mockExecuteCompensation(input))
                .description("""
                        执行补偿方案（退款/发放优惠券/退款+优惠券）。
                        参数：compensationType（REFUND/COUPON/REFUND_AND_COUPON）、orderId、userId、amount。
                        返回：执行结果。
                        """)
                .inputType(ExecuteCompensationInput.class)
                .build();

        FunctionToolCallback<RecordResolutionInput, String> resolutionTool = FunctionToolCallback.builder(
                        "recordResolution",
                        (RecordResolutionInput input) -> {
                            resolutionRecorded.set(true);
                            log.info("记录投诉结案：complaintId={}，outcome={}", complaintId, input.outcome());
                            return "结案记录成功：" + input.outcome();
                        })
                .description("""
                        记录投诉结案信息。在所有通知和补偿步骤完成后调用。
                        参数：caseId、outcome（结案摘要）。
                        """)
                .inputType(RecordResolutionInput.class)
                .build();

        FunctionToolCallback<SendNotificationInput, String> notificationTool = FunctionToolCallback.builder(
                        "sendUserNotification",
                        (SendNotificationInput input) -> {
                            log.info("通知用户：userId={}，message={}", input.userId(), input.message());
                            return "用户通知已发送";
                        })
                .description("""
                        向投诉用户发送处理进展通知。在 recordResolution 之后调用。
                        参数：userId、message（通知内容）。
                        """)
                .inputType(SendNotificationInput.class)
                .build();

        return ReactAgent.builder()
                .name("complaint-executor-agent")
                .chatClient(chatClient)
                .systemPrompt(buildSystemPrompt())
                .tools(notifyTool, compensationTool, resolutionTool, notificationTool)
                .compileConfig(CompileConfig.builder()
                        .recursionLimit(MAX_RECURSION_LIMIT)
                        .build())
                .build();
    }


    private String mockNotifyParty(NotifyPartyInput input) {
        log.info("【Mock】通知责任方：partyType={}，caseId={}，deadline={}",
                input.partyType(), input.caseId(), input.deadline());
        return switch (input.partyType().toUpperCase()) {
            case "MERCHANT" -> "商家已收到投诉通知，要求在 " + input.deadline() + " 内回复处理结果。";
            case "LOGISTICS" -> "物流公司已收到索赔通知，等待核查结果。";
            case "PLATFORM" -> "平台内部处理工单已创建，将优先跟进。";
            default -> "已通知 " + input.partyType() + "，等待处理。";
        };
    }

    private String mockExecuteCompensation(ExecuteCompensationInput input) {
        log.info("【Mock】执行补偿：type={}，orderId={}，amount={}", input.compensationType(), input.orderId(), input.amount());
        return switch (input.compensationType().toUpperCase()) {
            case "REFUND" -> "退款 ¥" + input.amount() + " 已发起，预计 3-5 个工作日到账。";
            case "COUPON" -> "补偿优惠券 ¥" + input.amount() + " 已发放至用户账户。";
            case "REFUND_AND_COUPON" -> "退款 ¥" + input.amount() + " 已发起，同时发放 ¥50 补偿优惠券。";
            default -> "补偿方案 " + input.compensationType() + " 已执行。";
        };
    }

    private String buildPlanMessage(ComplaintPlan plan) {
        return """
                请按以下已审批的投诉处理计划执行：

                - 案件 ID：%s
                - 责任方：%s
                - 责任认定理由：%s
                - 补偿方案：%s，金额 ¥%s
                - 联系顺序：%s
                - 订单 ID：见案件记录

                执行步骤：
                1. 按联系顺序依次调用 notifyParty 通知各责任方（deadline: 48h）
                2. 调用 executeCompensation 执行补偿方案
                3. 调用 recordResolution 记录结案摘要
                4. 调用 sendUserNotification 告知用户处理结果
                """.formatted(
                plan.getComplaintId(),
                plan.getResponsibleParty(),
                plan.getResponsibilityReason(),
                plan.getCompensationType(),
                plan.getCompensationAmount(),
                plan.getContactSequence()
        );
    }

    private String buildSystemPrompt() {
        return """
                你是投诉处理执行专员，负责严格按照审批通过的处理计划执行，不做任何额外判断。
                使用提供的工具完成所有执行步骤，每一步执行完后记录结果。
                所有步骤完成后，生成一句话的执行摘要。
                """;
    }

    // ---- 工具输入 DTO（内部使用） ----

    record NotifyPartyInput(String partyType, String caseId, String deadline) {}

    record ExecuteCompensationInput(String compensationType, String orderId, String userId, String amount) {}

    record RecordResolutionInput(String caseId, String outcome) {}

    record SendNotificationInput(String userId, String message) {}
}
