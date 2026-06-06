package com.enterprise.kb.search.profile;

import com.enterprise.kb.search.dto.QaExchangeSavedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ProfileActivityListener} 单测：聚焦 Kafka 投递成功不降级、同步异常与异步失败均降级到本地线程池。
 * 降级执行器用同线程（{@code Runnable::run}）便于同步断言。
 */
@ExtendWith(MockitoExtension.class)
class ProfileActivityListenerTest {

    @Mock
    KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    ProfileInferenceRunner inferenceRunner;

    private ProfileActivityListener listener;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new ProfileActivityListener(kafkaTemplate, inferenceRunner, Runnable::run);
        ReflectionTestUtils.setField(listener, "topic", "kb.user.activity");
    }

    @Test
    void sendsToKafkaWithoutFallbackOnSuccess() {
        CompletableFuture<SendResult<String, String>> ok = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq("kb.user.activity"), anyString(), anyString())).thenReturn(ok);
        listener.onQaExchangeSaved(new QaExchangeSavedEvent(userId));
        verify(kafkaTemplate).send("kb.user.activity", userId.toString(), userId.toString());
        verifyNoInteractions(inferenceRunner);
    }

    @Test
    void fallsBackToLocalInferenceOnAsyncFailure() {
        CompletableFuture<SendResult<String, String>> failed =
                CompletableFuture.failedFuture(new RuntimeException("broker down"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed);
        listener.onQaExchangeSaved(new QaExchangeSavedEvent(userId));
        verify(inferenceRunner).run(userId);
    }

    @Test
    void fallsBackToLocalInferenceOnSyncException() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("producer closed"));
        listener.onQaExchangeSaved(new QaExchangeSavedEvent(userId));
        verify(inferenceRunner).run(userId);
    }

    @Test
    void ignoresNullUserId() {
        listener.onQaExchangeSaved(new QaExchangeSavedEvent(null));
        verifyNoInteractions(kafkaTemplate, inferenceRunner);
    }
}
