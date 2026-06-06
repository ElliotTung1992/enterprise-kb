package com.enterprise.kb.search.profile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 用户画像离线推断的 Kafka 消费者（ADR-016，Phase 2）。
 *
 * <p>消费用户活动事件，解析 userId 后委派给 {@link ProfileInferenceRunner}（去抖+推断+写入逻辑，
 * 与 Kafka 降级路径共用）。仅当 {@code enterprise.kb.profile.inference.enabled=true} 时装配；
 * 关闭时无消费者、不连 Kafka。</p>
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
