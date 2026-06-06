package com.enterprise.kb.search.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link ProfileInferenceWorker} 单测：聚焦 userId 解析与委派 run
 * （去抖/推断逻辑已在 {@link ProfileInferenceRunnerTest} 覆盖）。
 */
@ExtendWith(MockitoExtension.class)
class ProfileInferenceWorkerTest {

    @Mock
    ProfileInferenceRunner inferenceRunner;

    private ProfileInferenceWorker worker;

    @BeforeEach
    void setUp() {
        worker = new ProfileInferenceWorker(inferenceRunner);
    }

    @Test
    void delegatesValidUserIdToRunner() {
        UUID userId = UUID.randomUUID();
        worker.onUserActivity(userId.toString());
        verify(inferenceRunner).run(userId);
    }

    @Test
    void ignoresInvalidUserId() {
        worker.onUserActivity("not-a-uuid");
        verifyNoInteractions(inferenceRunner);
    }

    @Test
    void ignoresBlankUserId() {
        worker.onUserActivity("   ");
        verifyNoInteractions(inferenceRunner);
    }
}
