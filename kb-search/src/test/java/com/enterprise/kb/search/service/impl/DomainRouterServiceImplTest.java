package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.Domain;
import com.enterprise.kb.search.TestPromptProviders;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.dto.ConversationState;
import com.enterprise.kb.search.dto.RoutingDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link DomainRouterServiceImpl} 单测：聚焦管道格式解析与 UNCLEAR 降级。
 * LLM 调用通过 mock {@link ModelProviderResolver} + 深桩 {@link ChatClient} 注入固定响应。
 */
@ExtendWith(MockitoExtension.class)
class DomainRouterServiceImplTest {

    @Mock ModelProviderResolver modelProviderResolver;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) ChatClient chatClient;

    private DomainRouterServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DomainRouterServiceImpl(modelProviderResolver, TestPromptProviders.local());
        ReflectionTestUtils.setField(service, "routerProvider", "LLAMA_CPP");
        ReflectionTestUtils.setField(service, "contextTurns", 3);
    }

    private void stubLlm(String response) {
        when(modelProviderResolver.resolveChatClient(any())).thenReturn(chatClient);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(response);
    }

    @Test
    void parsesAfterSalesDecision() {
        stubLlm("AFTER_SALES|NONE|NONE|用户明确提出退款诉求");

        RoutingDecision d = service.route(List.of(), ConversationState.initial(), "我要退款");

        assertThat(d.primaryDomain()).isEqualTo(Domain.AFTER_SALES);
        assertThat(d.secondary()).isEmpty();
        assertThat(d.runnerUp()).isNull();
        assertThat(d.evidence()).contains("退款");
        // 仅 4 段、无 EMOTION 段时，emotional 缺省为 false（向后兼容）
        assertThat(d.emotional()).isFalse();
    }

    @Test
    void parsesEmotionalFlagFromFifthSegment() {
        stubLlm("AFTER_SALES|NONE|NONE|情绪宣泄但维持售后域|EMOTIONAL");

        RoutingDecision d = service.route(List.of(), ConversationState.initial(), "你们太差劲了");

        assertThat(d.primaryDomain()).isEqualTo(Domain.AFTER_SALES);
        assertThat(d.emotional()).isTrue();
    }

    @Test
    void parsesComplaintWithSecondaryAndRunnerUp() {
        stubLlm("COMPLAINT|AFTER_SALES|UNCLEAR|含投诉与退款两个诉求");

        RoutingDecision d = service.route(List.of(), ConversationState.initial(), "投诉并退款");

        assertThat(d.primaryDomain()).isEqualTo(Domain.COMPLAINT);
        assertThat(d.secondary()).containsExactly(Domain.AFTER_SALES);
        assertThat(d.runnerUp()).isEqualTo(Domain.UNCLEAR);
    }

    @Test
    void secondaryListKeepsOnlyBusinessDomains() {
        stubLlm("AFTER_SALES|COMPLAINT,CHITCHAT,HANDOFF,NONE|NONE|测试次要域过滤");

        RoutingDecision d = service.route(List.of(), ConversationState.initial(), "x");

        assertThat(d.secondary()).containsExactly(Domain.COMPLAINT);
    }

    @Test
    void malformedOutputFallsBackToUnclear() {
        stubLlm("我觉得这是一个售后问题，建议走退款流程");

        RoutingDecision d = service.route(List.of(), ConversationState.initial(), "x");

        assertThat(d.primaryDomain()).isEqualTo(Domain.UNCLEAR);
    }

    @Test
    void picksParseableLineWhenLlmAddsProse() {
        stubLlm("好的，我的判断如下：\nAFTER_SALES|NONE|NONE|用户咨询换货流程");

        RoutingDecision d = service.route(List.of(), ConversationState.initial(), "x");

        assertThat(d.primaryDomain()).isEqualTo(Domain.AFTER_SALES);
        assertThat(d.evidence()).contains("换货");
    }

    @Test
    void llmExceptionFallsBackToUnclear() {
        when(modelProviderResolver.resolveChatClient(any()))
                .thenThrow(new RuntimeException("AI 服务不可用"));

        RoutingDecision d = service.route(List.of(), ConversationState.initial(), "我要退款");

        assertThat(d.primaryDomain()).isEqualTo(Domain.UNCLEAR);
    }

    @Test
    void blankMessageReturnsUnclearWithoutCallingLlm() {
        RoutingDecision d = service.route(List.of(), ConversationState.initial(), "   ");

        assertThat(d.primaryDomain()).isEqualTo(Domain.UNCLEAR);
        verifyNoInteractions(modelProviderResolver);
    }
}
