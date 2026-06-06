package com.enterprise.kb.user.service.impl;

import com.enterprise.kb.common.constants.AnswerLanguage;
import com.enterprise.kb.common.constants.AnswerLength;
import com.enterprise.kb.common.constants.Seniority;
import com.enterprise.kb.user.dto.InferredSignals;
import com.enterprise.kb.user.dto.ProfileInferenceState;
import com.enterprise.kb.user.dto.UpdateProfileRequest;
import com.enterprise.kb.user.dto.UserProfileView;
import com.enterprise.kb.user.mapper.UserProfileMapper;
import com.enterprise.kb.user.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProfileServiceImpl} 单测：聚焦 declared/inferred 合并优先级、统一推断逐字段写门、渲染与服务态视图。
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    @Mock
    UserProfileMapper mapper;

    private ProfileServiceImpl service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProfileServiceImpl(mapper);
        ReflectionTestUtils.setField(service, "injectionEnabled", true);
        ReflectionTestUtils.setField(service, "confidenceThreshold", 0.7);
    }

    private void stubProfile(String json) {
        UserProfile p = new UserProfile();
        p.setUserId(userId);
        p.setProfile(json);
        when(mapper.findByUserId(userId)).thenReturn(Optional.of(p));
    }

    private UserProfile captureUpsert() {
        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(mapper).upsert(captor.capture());
        return captor.getValue();
    }

    // ---- 渲染 ----

    @Test
    void renderBlockContainsExplicitLabels() {
        stubProfile("{\"declared\":{\"seniority\":\"INTERMEDIATE\",\"answerLength\":\"CONCISE\"}}");
        String block = service.renderProfileBlock(userId);
        assertThat(block).contains("<user_profile>").contains("中级").contains("简洁").contains("本轮");
    }

    @Test
    void renderBlockServesInferredWhenNoDeclared() {
        stubProfile("{\"inferred\":{\"answerLength\":\"CONCISE\",\"answerLanguage\":\"ZH\"}}");
        String block = service.renderProfileBlock(userId);
        assertThat(block).contains("简洁").contains("中文");
    }

    @Test
    void renderBlockEmptyWhenNoProfile() {
        when(mapper.findByUserId(userId)).thenReturn(Optional.empty());
        assertThat(service.renderProfileBlock(userId)).isEmpty();
    }

    @Test
    void renderBlockEmptyWhenPersonalizationDisabled() {
        stubProfile("{\"declared\":{\"seniority\":\"SENIOR\"},\"meta\":{\"personalizationEnabled\":false}}");
        assertThat(service.renderProfileBlock(userId)).isEmpty();
    }

    @Test
    void renderBlockEmptyWhenInjectionDisabled() {
        ReflectionTestUtils.setField(service, "injectionEnabled", false);
        assertThat(service.renderProfileBlock(userId)).isEmpty();
    }

    // ---- 服务态合并 ----

    @Test
    void getProfileExplicitSource() {
        stubProfile("{\"declared\":{\"seniority\":\"SENIOR\"}}");
        UserProfileView v = service.getProfile(userId);
        assertThat(v.seniority()).isEqualTo(Seniority.SENIOR);
        assertThat(v.senioritySource()).isEqualTo("EXPLICIT");
    }

    @Test
    void getProfileInferredSource() {
        stubProfile("{\"inferred\":{\"seniority\":\"JUNIOR\",\"answerLanguage\":\"ZH\"}}");
        UserProfileView v = service.getProfile(userId);
        assertThat(v.seniority()).isEqualTo(Seniority.JUNIOR);
        assertThat(v.senioritySource()).isEqualTo("INFERRED");
        assertThat(v.answerLanguage()).isEqualTo(AnswerLanguage.ZH);
        assertThat(v.answerLanguageSource()).isEqualTo("INFERRED");
    }

    @Test
    void declaredWinsOverInferred() {
        stubProfile("{\"declared\":{\"seniority\":\"SENIOR\"},\"inferred\":{\"seniority\":\"JUNIOR\"}}");
        UserProfileView v = service.getProfile(userId);
        assertThat(v.seniority()).isEqualTo(Seniority.SENIOR);
        assertThat(v.senioritySource()).isEqualTo("EXPLICIT");
    }

    // ---- 写入 ----

    @Test
    void updateDeclaredPersistsExplicitFields() {
        when(mapper.findByUserId(userId)).thenReturn(Optional.empty());
        service.updateDeclared(userId,
                new UpdateProfileRequest(Seniority.SENIOR, AnswerLength.DETAILED, null, null, null));
        UserProfile saved = captureUpsert();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getProfile()).contains("SENIOR").contains("DETAILED");
    }

    @Test
    void recordInferenceWritesQualifiedFieldsAndKeepsDeclared() {
        stubProfile("{\"declared\":{\"answerLength\":\"DETAILED\"}}");
        // 资历 0.82、语言 0.95 均达阈值；长度/风格无信号
        service.recordInference(userId,
                new InferredSignals(Seniority.INTERMEDIATE, 0.82, null, null, AnswerLanguage.ZH, 0.95, null, null), 12);
        UserProfile saved = captureUpsert();
        assertThat(saved.getProfile()).contains("INTERMEDIATE").contains("ZH").contains("DETAILED");
    }

    @Test
    void recordInferenceGatesLowConfidence() {
        when(mapper.findByUserId(userId)).thenReturn(Optional.empty());
        service.recordInference(userId,
                new InferredSignals(Seniority.SENIOR, 0.5, null, null, null, null, null, null), 20);
        UserProfile saved = captureUpsert();
        assertThat(saved.getProfile()).doesNotContain("SENIOR");       // 低置信不写
        assertThat(saved.getProfile()).contains("processedMsgCount");  // meta 仍刷新
    }

    @Test
    void recordInferenceKeepsOldWhenNewLowConfidence() {
        stubProfile("{\"inferred\":{\"seniority\":\"INTERMEDIATE\"}}");
        service.recordInference(userId,
                new InferredSignals(Seniority.JUNIOR, 0.4, null, null, null, null, null, null), 20);
        UserProfile saved = captureUpsert();
        assertThat(saved.getProfile()).contains("INTERMEDIATE").doesNotContain("JUNIOR"); // 保留旧、不被低置信覆盖
    }

    @Test
    void getInferenceStateReflectsDeclaredAndMeta() {
        stubProfile("{\"declared\":{\"seniority\":\"SENIOR\"},"
                + "\"meta\":{\"personalizationEnabled\":true,\"processedMsgCount\":7}}");
        ProfileInferenceState s = service.getInferenceState(userId);
        assertThat(s.hasDeclaredSeniority()).isTrue();
        assertThat(s.personalizationEnabled()).isTrue();
        assertThat(s.processedMsgCount()).isEqualTo(7);
    }
}
