package com.enterprise.kb.search.profile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 用户画像统一推断的 Kafka 消费者（ADR-016，Phase 2）。
 *
 * <p>消费用户活动事件（消息体为 userId），解析后委派给 {@link ProfileInferenceRunner#run}
 * （统一推断 4 维，与 Kafka 降级路径共用）。仅当 {@code enterprise.kb.profile.inference.enabled=true} 时装配。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "enterprise.kb.profile.inference.enabled", havingValue = "true")
public class ProfileInferenceWorker {

    private final ProfileInferenceRunner inferenceRunner;

    /**
     * 消费用户活动事件并委派推断。消息体为 userId 字符串。
     *
     * @param userIdRaw userId 字符串
     */
    @KafkaListener(
            topics = "${enterprise.kb.profile.inference.topic:kb.user.activity}",
            groupId = "${spring.kafka.consumer.group-id:profile-inference}")
    public void onUserActivity(String userIdRaw) {
        UUID userId = parseUserId(userIdRaw);
        if (userId != null) {
            inferenceRunner.run(userId);
        }
    }

    private UUID parseUserId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.strip());
        } catch (IllegalArgumentException e) {
            log.warn("跳过非法 userId 活动事件：{}", raw);
            return null;
        }
    }
}
