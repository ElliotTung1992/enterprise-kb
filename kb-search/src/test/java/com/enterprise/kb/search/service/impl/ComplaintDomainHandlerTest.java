package com.enterprise.kb.search.service.impl;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.enterprise.kb.search.TestPromptProviders;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.dto.DomainContext;
import com.enterprise.kb.search.dto.DomainResult;
import com.enterprise.kb.search.model.Complaint;
import com.enterprise.kb.search.service.ComplaintEscalationService;
import com.enterprise.kb.search.service.ComplaintWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ComplaintDomainHandler} 单测。
 * <p>{@code escalateComplaint} 工具回调直接验证校验与服务编排；{@code handle} 经
 * 子类覆写 {@code buildAgent} 注入 mock Agent，验证回复包装。</p>
 */
@ExtendWith(MockitoExtension.class)
class ComplaintDomainHandlerTest {

    @Mock ModelProviderResolver modelProviderResolver;
    @Mock RedisSaver agentCheckpointSaver;
    @Mock ComplaintEscalationService complaintEscalationService;
    @Mock ComplaintWorkflowService complaintWorkflowService;
    @Mock ReactAgent mockAgent;

    private ComplaintDomainHandler handler;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new ComplaintDomainHandler(modelProviderResolver, agentCheckpointSaver,
                complaintEscalationService, complaintWorkflowService, TestPromptProviders.local());
    }

    private DomainContext ctx() {
        return new DomainContext(UUID.randomUUID(), userId, "投诉", List.of(), null);
    }

    @Test
    void escalateComplaintRejectsBlankOrderId() {
        String result = handler.escalateComplaint(ctx(),
                new ComplaintDomainHandler.EscalateComplaintInput("  ", "描述"));

        assertThat(result).contains("订单号");
        verifyNoInteractions(complaintEscalationService, complaintWorkflowService);
    }

    @Test
    void escalateComplaintRejectsBlankDescription() {
        String result = handler.escalateComplaint(ctx(),
                new ComplaintDomainHandler.EscalateComplaintInput("ORD-1", ""));

        assertThat(result).contains("投诉内容");
        verifyNoInteractions(complaintEscalationService, complaintWorkflowService);
    }

    @Test
    void escalateComplaintCreatesComplaintAndStartsPlanning() {
        UUID complaintId = UUID.randomUUID();
        Complaint complaint = new Complaint();
        complaint.setId(complaintId);
        when(complaintEscalationService.createComplaint(eq(userId), eq("ORD-1"), anyString()))
                .thenReturn(complaint);

        String result = handler.escalateComplaint(ctx(),
                new ComplaintDomainHandler.EscalateComplaintInput("ORD-1", "多次投诉未解决"));

        assertThat(result).contains(complaintId.toString());
        verify(complaintWorkflowService).startPlanning(complaintId);
    }

    @Test
    void escalateComplaintReturnsGracefulMessageOnServiceFailure() {
        when(complaintEscalationService.createComplaint(any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("DB down"));

        String result = handler.escalateComplaint(ctx(),
                new ComplaintDomainHandler.EscalateComplaintInput("ORD-1", "描述"));

        assertThat(result).contains("失败");
    }

    /** Agent 实际调过 escalateComplaint（toolInvoked=true）后回复"已升级" → 原样透传。 */
    @Test
    void handlePassesThroughWhenToolActuallyInvoked() throws Exception {
        ComplaintDomainHandler seamHandler = new ComplaintDomainHandler(
                modelProviderResolver, agentCheckpointSaver,
                complaintEscalationService, complaintWorkflowService, TestPromptProviders.local()) {
            @Override
            ReactAgent buildAgent(DomainContext c, AtomicBoolean toolInvoked) {
                // 模拟 Agent 在 call() 期间真正调过工具
                toolInvoked.set(true);
                return mockAgent;
            }
        };
        when(mockAgent.call(anyList(), any()))
                .thenReturn(new AssistantMessage("您的投诉已升级，案件编号：abc-123"));

        DomainResult result = seamHandler.handle(ctx());

        assertThat(result.answer()).contains("已升级");
        assertThat(result.answer()).contains("abc-123");
    }

    /**
     * 关键回归：Agent 在没调 escalateComplaint 工具的情况下**伪造案件编号**——这是确凿的硬伪造，
     * 必须被拦截并替换为安全话术，避免用户拿着假编号去查询。
     */
    @Test
    void handleInterceptsFabricatedCaseId() throws Exception {
        ComplaintDomainHandler seamHandler = new ComplaintDomainHandler(
                modelProviderResolver, agentCheckpointSaver,
                complaintEscalationService, complaintWorkflowService, TestPromptProviders.local()) {
            @Override
            ReactAgent buildAgent(DomainContext c, AtomicBoolean toolInvoked) {
                // 故意不动 toolInvoked → 模拟 LLM 跳过工具直接输出
                return mockAgent;
            }
        };
        when(mockAgent.call(anyList(), any())).thenReturn(new AssistantMessage(
                "您的投诉已成功升级，案件编号：CMPL-2026-0520-007，专员将尽快跟进处理。"));

        DomainResult result = seamHandler.handle(ctx());

        assertThat(result.answer()).contains("没有真正提交");
        assertThat(result.answer()).doesNotContain("CMPL-2026-0520-007");
        verifyNoInteractions(complaintEscalationService, complaintWorkflowService);
    }

    /**
     * 关键设计取舍（A 方案）：Agent 没调工具但只口头说"已升级 / 稍后专员"——
     * **不再拦截**。理由：单纯文本启发会把"信息收集阶段交代后续流程"误抓为幻觉，
     * 改靠 prompt 约束 + 漏抓接受。只要 Agent 没伪造案件编号，就放行。
     */
    @Test
    void handlePassesThroughVagueClaimWithoutFabricatedId() throws Exception {
        ComplaintDomainHandler seamHandler = new ComplaintDomainHandler(
                modelProviderResolver, agentCheckpointSaver,
                complaintEscalationService, complaintWorkflowService, TestPromptProviders.local()) {
            @Override
            ReactAgent buildAgent(DomainContext c, AtomicBoolean toolInvoked) {
                return mockAgent;
            }
        };
        when(mockAgent.call(anyList(), any())).thenReturn(new AssistantMessage(
                "我现在为您升级投诉，稍后会有专员审核处理，结果会通知您。"));

        DomainResult result = seamHandler.handle(ctx());

        // A 方案故意放行：没有伪造编号 → 不拦截。Prompt 兜底要求 Agent 报编号。
        assertThat(result.answer()).contains("升级投诉");
        assertThat(result.answer()).doesNotContain("没有真正提交");
    }

    /** 普通收信息 / 引导话术，照常透传，不该被误拦截。 */
    @Test
    void handleDoesNotInterceptInformationGatheringReply() throws Exception {
        ComplaintDomainHandler seamHandler = new ComplaintDomainHandler(
                modelProviderResolver, agentCheckpointSaver,
                complaintEscalationService, complaintWorkflowService, TestPromptProviders.local()) {
            @Override
            ReactAgent buildAgent(DomainContext c, AtomicBoolean toolInvoked) {
                return mockAgent;
            }
        };
        when(mockAgent.call(anyList(), any())).thenReturn(new AssistantMessage(
                "为了帮您升级，请您提供订单号和具体投诉详情。"));

        DomainResult result = seamHandler.handle(ctx());

        assertThat(result.answer()).contains("请您提供");
        assertThat(result.answer()).doesNotContain("没有真正提交");
    }

    /**
     * 回归（生产实测）：Agent 在补全槽位阶段提到"投诉详情"和"为您跟进处理"是正常引导。
     * 旧版 regex 把这类话术误判为已升级宣称，需保持不拦截。
     */
    @Test
    void handleDoesNotInterceptComplaintDetailGathering() throws Exception {
        ComplaintDomainHandler seamHandler = new ComplaintDomainHandler(
                modelProviderResolver, agentCheckpointSaver,
                complaintEscalationService, complaintWorkflowService, TestPromptProviders.local()) {
            @Override
            ReactAgent buildAgent(DomainContext c, AtomicBoolean toolInvoked) {
                return mockAgent;
            }
        };
        when(mockAgent.call(anyList(), any())).thenReturn(new AssistantMessage("""
                感谢您提供订单号！为了完整记录您的投诉，请您再补充一下：

                **投诉详情**：您提到处理太慢，请问具体是什么事情处理慢呢？比如：
                - 是之前申请售后/退货处理慢？
                - 是退款到账慢？
                - 还是其他什么问题？

                您能简单描述一下具体经过吗？这样专员可以更好地为您跟进处理。"""));

        DomainResult result = seamHandler.handle(ctx());

        assertThat(result.answer()).contains("请您再补充");
        assertThat(result.answer()).contains("投诉详情");
        assertThat(result.answer()).doesNotContain("没有真正提交");
    }
}
