package com.enterprise.kb.search.profile;

import com.enterprise.kb.common.constants.Seniority;
import com.enterprise.kb.search.dto.SeniorityInference;
import com.enterprise.kb.search.mapper.QaChatMessageMapper;
import com.enterprise.kb.search.service.SeniorityInferenceService;
import com.enterprise.kb.user.dto.ProfileInferenceState;
import com.enterprise.kb.user.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ProfileInferenceRunner} 单测：聚焦去抖（开关/已声明/新消息增量/时间间隔）、置信闸应用与异常吞咽。
 */
@ExtendWith(MockitoExtension.class)
class ProfileInferenceRunnerTest {

    @Mock
    ProfileService profileService;
    @Mock
    QaChatMessageMapper messageMapper;
    @Mock
    SeniorityInferenceService seniorityInferenceService;

    private ProfileInferenceRunner runner;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        runner = new ProfileInferenceRunner(profileService, messageMapper, seniorityInferenceService);
        ReflectionTestUtils.setField(runner, "recentMessageLimit", 50);
        ReflectionTestUtils.setField(runner, "minNewMessages", 10);
        ReflectionTestUtils.setField(runner, "minIntervalHours", 6L);
        ReflectionTestUtils.setField(runner, "confidenceThreshold", 0.7);
    }

    private void stubState(boolean declared, boolean enabled, Instant last, int processed) {
        when(profileService.getInferenceState(userId))
                .thenReturn(new ProfileInferenceState(declared, enabled, last, processed));
    }

    @Test
    void skipsWhenPersonalizationDisabled() {
        stubState(false, false, null, 0);
        runner.run(userId);
        verifyNoInteractions(seniorityInferenceService);
        verify(profileService, never()).recordInference(any(), any(), any(), anyInt());
    }

    @Test
    void skipsWhenDeclaredSeniorityPresent() {
        stubState(true, true, null, 0);
        runner.run(userId);
        verifyNoInteractions(seniorityInferenceService);
    }

    @Test
    void skipsWhenNotEnoughNewMessages() {
        stubState(false, true, null, 5);
        when(messageMapper.countUserQuestions(userId)).thenReturn(9); // 9-5=4 < 10
        runner.run(userId);
        verifyNoInteractions(seniorityInferenceService);
    }

    @Test
    void skipsWhenTooRecent() {
        stubState(false, true, Instant.now().minus(Duration.ofHours(1)), 0);
        when(messageMapper.countUserQuestions(userId)).thenReturn(20);
        runner.run(userId);
        verifyNoInteractions(seniorityInferenceService);
    }

    @Test
    void appliesWhenConfident() {
        stubState(false, true, null, 0);
        when(messageMapper.countUserQuestions(userId)).thenReturn(15);
        when(messageMapper.findRecentUserQuestions(userId, 50)).thenReturn(List.of("q1", "q2"));
        when(seniorityInferenceService.infer(anyList()))
                .thenReturn(Optional.of(new SeniorityInference(Seniority.SENIOR, 0.8, "r")));
        runner.run(userId);
        verify(profileService).recordInference(userId, Seniority.SENIOR, 0.8, 15);
    }

    @Test
    void touchesMetaWhenNotConfident() {
        stubState(false, true, null, 0);
        when(messageMapper.countUserQuestions(userId)).thenReturn(15);
        when(messageMapper.findRecentUserQuestions(userId, 50)).thenReturn(List.of("q1"));
        when(seniorityInferenceService.infer(anyList()))
                .thenReturn(Optional.of(new SeniorityInference(Seniority.SENIOR, 0.5, "r")));
        runner.run(userId);
        verify(profileService).recordInference(userId, null, null, 15);
    }

    @Test
    void nullUserIdIsNoop() {
        runner.run(null);
        verifyNoInteractions(profileService, messageMapper, seniorityInferenceService);
    }

    @Test
    void swallowsExceptions() {
        when(profileService.getInferenceState(userId)).thenThrow(new RuntimeException("db down"));
        runner.run(userId); // 不应抛出
        verifyNoInteractions(seniorityInferenceService);
    }
}
