package com.enterprise.kb.user.service.impl;

import com.enterprise.kb.common.constants.AnswerLength;
import com.enterprise.kb.common.constants.Seniority;
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
 * {@link ProfileServiceImpl} 单测：聚焦 declared/inferred 合并优先级、置信闸、渲染与写入。
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

    @Test
    void renderBlockContainsExplicitLabels() {
        stubProfile("{\"declared\":{\"seniority\":\"INTERMEDIATE\",\"answerLength\":\"CONCISE\"}}");
        String block = service.renderProfileBlock(userId);
        assertThat(block).contains("<user_profile>").contains("中级").contains("简洁").contains("本轮");
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

    @Test
    void getProfileExplicitSource() {
        stubProfile("{\"declared\":{\"seniority\":\"SENIOR\"}}");
        UserProfileView v = service.getProfile(userId);
        assertThat(v.seniority()).isEqualTo(Seniority.SENIOR);
        assertThat(v.senioritySource()).isEqualTo("EXPLICIT");
        assertThat(v.seniorityConfidence()).isNull();
    }

    @Test
    void getProfileInferredServedWhenAboveThreshold() {
        stubProfile("{\"inferred\":{\"seniority\":\"JUNIOR\",\"confidence\":0.8}}");
        UserProfileView v = service.getProfile(userId);
        assertThat(v.seniority()).isEqualTo(Seniority.JUNIOR);
        assertThat(v.senioritySource()).isEqualTo("INFERRED");
        assertThat(v.seniorityConfidence()).isEqualTo(0.8);
    }

    @Test
    void getProfileInferredIgnoredBelowThreshold() {
        stubProfile("{\"inferred\":{\"seniority\":\"JUNIOR\",\"confidence\":0.5}}");
        UserProfileView v = service.getProfile(userId);
        assertThat(v.seniority()).isNull();
        assertThat(v.senioritySource()).isNull();
    }

    @Test
    void declaredWinsOverInferred() {
        stubProfile("{\"declared\":{\"seniority\":\"SENIOR\"},"
                + "\"inferred\":{\"seniority\":\"JUNIOR\",\"confidence\":0.95}}");
        UserProfileView v = service.getProfile(userId);
        assertThat(v.seniority()).isEqualTo(Seniority.SENIOR);
        assertThat(v.senioritySource()).isEqualTo("EXPLICIT");
    }

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
    void recordInferenceWritesInferredAndKeepsDeclared() {
        stubProfile("{\"declared\":{\"answerLength\":\"CONCISE\"}}");
        service.recordInference(userId, Seniority.INTERMEDIATE, 0.82, 12);
        UserProfile saved = captureUpsert();
        // 推断资历写入、显式答案长度保留
        assertThat(saved.getProfile()).contains("INTERMEDIATE").contains("CONCISE");
    }

    @Test
    void recordInferenceNullSeniorityOnlyTouchesMeta() {
        stubProfile("{\"inferred\":{\"seniority\":\"SENIOR\",\"confidence\":0.9}}");
        service.recordInference(userId, null, null, 20);
        UserProfile saved = captureUpsert();
        assertThat(saved.getProfile()).contains("SENIOR").contains("processedMsgCount");
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
