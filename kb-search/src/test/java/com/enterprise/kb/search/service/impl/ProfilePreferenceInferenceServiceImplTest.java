package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.AnswerLanguage;
import com.enterprise.kb.common.constants.AnswerStyle;
import com.enterprise.kb.common.constants.Seniority;
import com.enterprise.kb.search.TestPromptProviders;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.user.dto.InferredSignals;
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
 * {@link ProfilePreferenceInferenceServiceImpl} 单测：四行 FIELD|VALUE|CONFIDENCE 解析、NONE/置信夹取、降级为空。
 */
@ExtendWith(MockitoExtension.class)
class ProfilePreferenceInferenceServiceImplTest {

    @Mock
    ModelProviderResolver modelProviderResolver;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    ChatClient chatClient;

    private ProfilePreferenceInferenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProfilePreferenceInferenceServiceImpl(modelProviderResolver, TestPromptProviders.local());
        ReflectionTestUtils.setField(service, "provider", "LLAMA_CPP");
    }

    private void stubLlm(String response) {
        when(modelProviderResolver.resolveChatClient(any())).thenReturn(chatClient);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn(response);
    }

    @Test
    void parsesFourDimensions() {
        stubLlm("SENIORITY|INTERMEDIATE|0.78\nLENGTH|NONE|0\nLANGUAGE|ZH|0.95\nSTYLE|WITH_EXAMPLES|0.6");
        InferredSignals s = service.infer(List.of("以后用中文", "如何实现 X"));
        assertThat(s.seniority()).isEqualTo(Seniority.INTERMEDIATE);
        assertThat(s.seniorityConfidence()).isEqualTo(0.78);
        assertThat(s.answerLength()).isNull();              // NONE
        assertThat(s.answerLanguage()).isEqualTo(AnswerLanguage.ZH);
        assertThat(s.answerLanguageConfidence()).isEqualTo(0.95);
        assertThat(s.answerStyle()).isEqualTo(AnswerStyle.WITH_EXAMPLES);
    }

    @Test
    void clampsConfidenceAboveOne() {
        stubLlm("LANGUAGE|ZH|1.5");
        assertThat(service.infer(List.of("q")).answerLanguageConfidence()).isEqualTo(1.0);
    }

    @Test
    void noneAndGarbageYieldNulls() {
        stubLlm("SENIORITY|NONE|0\n这是一段废话");
        InferredSignals s = service.infer(List.of("q"));
        assertThat(s.seniority()).isNull();
        assertThat(s.answerLanguage()).isNull();
    }

    @Test
    void exceptionYieldsEmpty() {
        when(modelProviderResolver.resolveChatClient(any())).thenThrow(new RuntimeException("AI 不可用"));
        InferredSignals s = service.infer(List.of("q"));
        assertThat(s.seniority()).isNull();
        assertThat(s.answerLanguage()).isNull();
    }

    @Test
    void emptyInputSkipsLlm() {
        InferredSignals s = service.infer(List.of());
        assertThat(s.seniority()).isNull();
        verifyNoInteractions(modelProviderResolver);
    }
}
