package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.AnswerLanguage;
import com.enterprise.kb.common.constants.AnswerLength;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.user.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AnswerPreferenceCaptureServiceImpl} 单测：预过滤、持久命中写入+回执、一次性/NONE/非法值不写、关闭与 LLM 失败降级。
 */
@ExtendWith(MockitoExtension.class)
class AnswerPreferenceCaptureServiceImplTest {

    @Mock
    ModelProviderResolver modelProviderResolver;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    ChatClient chatClient;
    @Mock
    ProfileService profileService;

    private AnswerPreferenceCaptureServiceImpl service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AnswerPreferenceCaptureServiceImpl(modelProviderResolver, profileService);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "provider", "LLAMA_CPP");
    }

    private void stubLlm(String response) {
        when(modelProviderResolver.resolveChatClient(any())).thenReturn(chatClient);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn(response);
    }

    @Test
    void preFilterSkipsNonPreferenceMessage() {
        // 无持久线索 → 预过滤直接跳过，零 LLM、零写入
        assertThat(service.capture(userId, "退款怎么操作")).isEmpty();
        verifyNoInteractions(modelProviderResolver, profileService);
    }

    @Test
    void durableLanguagePersistsAndNotifies() {
        stubLlm("LANGUAGE|ZH|YES");
        Optional<String> r = service.capture(userId, "以后用中文回复我");
        assertThat(r).isPresent();
        assertThat(r.get()).contains("中文");
        verify(profileService).mergeDeclaredPreference(eq(userId), eq(AnswerLanguage.ZH), isNull(), isNull());
    }

    @Test
    void durableLengthPersists() {
        stubLlm("LENGTH|CONCISE|YES");
        assertThat(service.capture(userId, "以后回答简洁点")).isPresent();
        verify(profileService).mergeDeclaredPreference(eq(userId), isNull(), eq(AnswerLength.CONCISE), isNull());
    }

    @Test
    void oneOffNotPersisted() {
        stubLlm("LENGTH|DETAILED|NO");
        assertThat(service.capture(userId, "以后再说吧，这次给我详细点")).isEmpty();
        verify(profileService, never()).mergeDeclaredPreference(any(), any(), any(), any());
    }

    @Test
    void noneNotPersisted() {
        stubLlm("NONE|NONE|NO");
        assertThat(service.capture(userId, "以后我再问你别的")).isEmpty();
        verify(profileService, never()).mergeDeclaredPreference(any(), any(), any(), any());
    }

    @Test
    void invalidValueNotPersisted() {
        stubLlm("LANGUAGE|FRENCH|YES"); // FRENCH 非合法枚举 → 解析失败、不写
        assertThat(service.capture(userId, "以后用法语回复")).isEmpty();
        verify(profileService, never()).mergeDeclaredPreference(any(), any(), any(), any());
    }

    @Test
    void disabledNoop() {
        ReflectionTestUtils.setField(service, "enabled", false);
        assertThat(service.capture(userId, "以后用中文回复我")).isEmpty();
        verifyNoInteractions(modelProviderResolver, profileService);
    }

    @Test
    void llmFailureReturnsEmpty() {
        when(modelProviderResolver.resolveChatClient(any())).thenThrow(new RuntimeException("AI down"));
        assertThat(service.capture(userId, "以后用中文回复我")).isEmpty();
        verify(profileService, never()).mergeDeclaredPreference(any(), any(), any(), any());
    }
}
