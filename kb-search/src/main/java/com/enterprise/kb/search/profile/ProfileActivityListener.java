package com.enterprise.kb.search.profile;

import com.enterprise.kb.search.dto.QaExchangeSavedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 用户活动事件 → Kafka 生产者 + 线程池降级（ADR-016，Phase 2）。
 *
 * <p>问答持久化事务提交后，把 userId 投递到活动 topic，驱动离线画像推断。
 * <b>Kafka 不可用时降级</b>：投递的同步异常或异步失败都转到本地有界线程池
 * （{@code profileInferenceExecutor}）直接跑推断，使 Kafka 故障下功能仍以「单机异步」形态可用。
 * 仅当 {@code enterprise.kb.profile.inference.enabled=true} 时装配。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "enterprise.kb.profile.inference.enabled", havingValue = "true")
public class ProfileActivityListener {

    /** 活动事件 topic 名（与 worker、ProfileKafkaConfig 共用）。 */
    @Value("${enterprise.kb.profile.inference.topic:kb.user.activity}")
    private String topic;

    private final KafkaTemplate<String, String> kafkaTemplate;
    /** 推断执行单元；Kafka 降级时本地直接调用。 */
    private final ProfileInferenceRunner inferenceRunner;
    /** Kafka 降级线程池（按 bean 名注入，有界、满则丢弃，提供背压）。 */
    private final Executor profileInferenceExecutor;

    /**
     * 在问答持久化事务提交后投递活动事件；投递失败降级到本地线程池推断。
     * key=userId 保证单用户内有序；消费侧（或降级 runner）再做去抖。
     *
     * @param event 问答交换已保存事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQaExchangeSaved(QaExchangeSavedEvent event) {
        UUID userId = event.userId();
        if (userId == null) {
            return;
        }
        String key = userId.toString();
        try {
            // send 非阻塞返回 future；broker 不可用时由 producer max.block.ms 快速失败（见 application.yml）。
            kafkaTemplate.send(topic, key, key).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("Kafka 投递失败，降级本地线程池推断：userId={}", userId, ex);
                    fallbackToLocalInference(userId);
                }
            });
        } catch (Exception e) {
            // 同步失败：broker 元数据超时（max.block.ms）/ 序列化 / producer 已关闭等。
            log.warn("Kafka 投递异常，降级本地线程池推断：userId={}", userId, e);
            fallbackToLocalInference(userId);
        }
    }

    /** 降级：把推断转到本地有界线程池；池+队列已满则放弃本次（尽力而为，下次活动再补）。 */
    private void fallbackToLocalInference(UUID userId) {
        try {
            profileInferenceExecutor.execute(() -> inferenceRunner.run(userId));
        } catch (RejectedExecutionException rej) {
            log.warn("画像降级线程池已满，丢弃本次推断：userId={}", userId);
        }
    }
}
