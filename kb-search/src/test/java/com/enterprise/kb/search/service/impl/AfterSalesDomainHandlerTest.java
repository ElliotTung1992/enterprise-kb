package com.enterprise.kb.search.service.impl;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.enterprise.kb.search.TestPromptProviders;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.dto.DomainContext;
import com.enterprise.kb.search.dto.DomainResult;
import com.enterprise.kb.search.service.ReviewRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AfterSalesDomainHandler} 单测。
 * <p>经子类覆写 {@code buildAgent} 注入 mock Agent，验证普通回复包装与
 * HITL 工具拦截（写 review_requests + 返回"审核中"终态）。</p>
 */
@ExtendWith(MockitoExtension.class)
class AfterSalesDomainHandlerTest {

    @Mock ModelProviderResolver modelProviderResolver;
    @Mock RedisSaver agentCheckpointSaver;
    @Mock ReviewRequestService reviewRequestService;
    @Mock ReactAgent mockAgent;

    private AfterSalesDomainHandler handlerWith(ReactAgent agent) {
        return new AfterSalesDomainHandler(modelProviderResolver, agentCheckpointSaver,
                reviewRequestService, new ObjectMapper(), TestPromptProviders.local()) {
            @Override
            ReactAgent buildAgent(String modelProvider) {
                return agent;
            }
        };
    }

    private DomainContext ctx() {
        return new DomainContext(UUID.randomUUID(), UUID.randomUUID(), "我要退款", List.of(), null);
    }

    @Test
    void handleWrapsPlainAgentAnswer() throws Exception {
        when(mockAgent.call(anyList(), any()))
                .thenReturn(new AssistantMessage("请问您要退哪个订单的商品？"));

        DomainResult result = handlerWith(mockAgent).handle(ctx());

        assertThat(result.answer()).isEqualTo("请问您要退哪个订单的商品？");
    }

    @Test
    void handleInterceptsSubmitToolCallAsHitl() throws Exception {
        AssistantMessage.ToolCall submitCall = new AssistantMessage.ToolCall(
                "tc-1", "function", "submitAfterSalesReview",
                "{\"orderId\":\"ORD-1\",\"reason\":\"购买7日内退款\"}");
        // AssistantMessage 带 toolCall 的构造器为 protected，mock 出所需形状
        AssistantMessage withToolCall = mock(AssistantMessage.class);
        when(withToolCall.hasToolCalls()).thenReturn(true);
        when(withToolCall.getToolCalls()).thenReturn(List.of(submitCall));
        when(mockAgent.call(anyList(), any())).thenReturn(withToolCall);

        DomainResult result = handlerWith(mockAgent).handle(ctx());

        assertThat(result.answer()).contains("已提交");
        assertThat(result.awaitingSlot()).isFalse();
        verify(reviewRequestService).createPending(
                any(), any(), any(), eq("ORD-1"), any(), any(), any(), any(), any());
    }
}
