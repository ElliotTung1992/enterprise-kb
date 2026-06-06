package com.enterprise.kb.search.profile;

import com.enterprise.kb.search.mapper.QaChatMessageMapper;
import com.enterprise.kb.search.service.ProfilePreferenceInferenceService;
import com.enterprise.kb.user.dto.InferredSignals;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ProfileInferenceRunner} 单测：聚焦去抖（开关/新消息增量/时间间隔）、到点触发统一推断、异常吞咽。
 * 置信写门在 {@code ProfileService.recordInference}，由 {@code ProfileServiceImplTest} 覆盖。
 */
@ExtendWith(MockitoExtension.class)
class ProfileInferenceRunnerTest {

    @Mock
    ProfileService profileService;
    @Mock
    QaChatMessageMapper messageMapper;
    @Mock
    ProfilePreferenceInferenceService inferenceService;

    private ProfileInferenceRunner runner;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        runner = new ProfileInferenceRunner(profileService, messageMapper, inferenceService);
        ReflectionTestUtils.setField(runner, "recentMessageLimit", 50);
        ReflectionTestUtils.setField(runner, "minNewMessages", 10);
        ReflectionTestUtils.setField(runner, "minIntervalHours", 6L);
    }

    private void stubState(boolean enabled, Instant last, int processed) {
        when(profileService.getInferenceState(userId))
                .thenReturn(new ProfileInferenceState(false, enabled, last, processed));
    }

    @Test
    void skipsWhenPersonalizationDisabled() {
        stubState(false, null, 0);
        runner.run(userId);
        verifyNoInteractions(inferenceService);
        verify(profileService, never()).recordInference(any(), any(), anyInt());
    }

    @Test
    void skipsWhenNotEnoughNewMessages() {
        stubState(true, null, 5);
        when(messageMapper.countUserQuestions(userId)).thenReturn(9); // 9-5=4 < 10
        runner.run(userId);
        verifyNoInteractions(inferenceService);
    }

    @Test
    void skipsWhenTooRecent() {
        stubState(true, Instant.now().minus(Duration.ofHours(1)), 0);
        when(messageMapper.countUserQuestions(userId)).thenReturn(20);
        runner.run(userId);
        verifyNoInteractions(inferenceService);
    }

    @Test
    void infersAndRecordsWhenDue() {
        stubState(true, null, 0);
        when(messageMapper.countUserQuestions(userId)).thenReturn(15);
        when(messageMapper.findRecentUserQuestions(userId, 50)).thenReturn(List.of("以后用中文", "如何实现 X"));
        when(inferenceService.infer(anyList())).thenReturn(InferredSignals.empty());
        runner.run(userId);
        verify(profileService).recordInference(eq(userId), any(InferredSignals.class), eq(15));
    }

    @Test
    void nullUserIdIsNoop() {
        runner.run(null);
        verifyNoInteractions(profileService, messageMapper, inferenceService);
    }

    @Test
    void swallowsExceptions() {
        when(profileService.getInferenceState(userId)).thenThrow(new RuntimeException("db down"));
        runner.run(userId); // 不应抛出
        verifyNoInteractions(inferenceService);
    }
}
