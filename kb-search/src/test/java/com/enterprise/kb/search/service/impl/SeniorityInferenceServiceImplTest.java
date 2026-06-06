package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.Seniority;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.dto.SeniorityInference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SeniorityInferenceServiceImpl} 单测：聚焦管道格式解析、置信夹取与降级为空。
 */
@ExtendWith(MockitoExtension.class)
class SeniorityInferenceServiceImplTest {

    @Mock
    ModelProviderResolver modelProviderResolver;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    ChatClient chatClient;

    private SeniorityInferenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SeniorityInferenceServiceImpl(modelProviderResolver);
        ReflectionTestUtils.setField(service, "provider", "LLAMA_CPP");
    }

    private void stubLlm(String response) {
        when(modelProviderResolver.resolveChatClient(any())).thenReturn(chatClient);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn(response);
    }

    @Test
    void parsesValidInference() {
        stubLlm("INTERMEDIATE|0.78|能正确使用术语并询问实现细节");
        Optional<SeniorityInference> r = service.infer(List.of("如何实现 X", "为什么 Y"));
        assertThat(r).isPresent();
        assertThat(r.get().seniority()).isEqualTo(Seniority.INTERMEDIATE);
        assertThat(r.get().confidence()).isEqualTo(0.78);
    }

    @Test
    void clampsConfidenceAboveOne() {
        stubLlm("SENIOR|1.5|越界置信");
        assertThat(service.infer(List.of("q")).get().confidence()).isEqualTo(1.0);
    }

    @Test
    void malformedFallsBackToEmpty() {
        stubLlm("我觉得这个用户是中级水平");
        assertThat(service.infer(List.of("q"))).isEmpty();
    }

    @Test
    void picksParseableLineAmidProse() {
        stubLlm("判断如下：\nJUNIOR|0.6|多为基础概念提问");
        assertThat(service.infer(List.of("q")).get().seniority()).isEqualTo(Seniority.JUNIOR);
    }

    @Test
    void exceptionFallsBackToEmpty() {
        when(modelProviderResolver.resolveChatClient(any())).thenThrow(new RuntimeException("AI 不可用"));
        assertThat(service.infer(List.of("q"))).isEmpty();
    }

    @Test
    void emptyInputSkipsLlm() {
        assertThat(service.infer(List.of())).isEmpty();
        verifyNoInteractions(modelProviderResolver);
    }
}
