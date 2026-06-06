package com.enterprise.kb.search.profile;

import com.enterprise.kb.search.mapper.QaChatMessageMapper;
import com.enterprise.kb.search.service.ProfilePreferenceInferenceService;
import com.enterprise.kb.user.dto.InferredSignals;
import com.enterprise.kb.user.dto.ProfileInferenceState;
import com.enterprise.kb.user.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 用户画像统一推断执行单元（ADR-016，Phase 2）。
 *
 * <p>去抖（个性化开关 / 新消息增量 / 时间间隔）→ 取近 N 条用户消息 → 一次 LLM 推断 4 维
 * → {@link ProfileService#recordInference} 逐字段过置信闸写入。用户明说的偏好（如"以后用中文"）
 * 在这条消息进入近期消息后被同一推断识别为高置信、写入 inferred 层。</p>
 *
 * <p>被 Kafka 消费者 {@link ProfileInferenceWorker} 与 {@link ProfileActivityListener} 的降级线程池共用；
 * 按用户去抖天然幂等；异常内部吞咽（不让毒丸消息反复重投或拖垮线程池）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "enterprise.kb.profile.inference.enabled", havingValue = "true")
public class ProfileInferenceRunner {

    /** 推断取用的近 N 条本人消息。 */
    @Value("${enterprise.kb.profile.inference.recent-message-limit:50}")
    private int recentMessageLimit;
    /** 去抖：距上次推断新消息 ≥ 此值才重算。 */
    @Value("${enterprise.kb.profile.inference.min-new-messages:10}")
    private int minNewMessages;
    /** 去抖：距上次推断间隔 ≥ 此小时数才重算。 */
    @Value("${enterprise.kb.profile.inference.min-interval-hours:6}")
    private long minIntervalHours;

    private final ProfileService profileService;
    private final QaChatMessageMapper messageMapper;
    private final ProfilePreferenceInferenceService inferenceService;

    /**
     * 对指定用户执行一次（去抖后的）统一画像推断。
     *
     * @param userId 用户 ID
     */
    public void run(UUID userId) {
        if (userId == null) {
            return;
        }
        try {
            ProfileInferenceState state = profileService.getInferenceState(userId);
            if (!state.personalizationEnabled()) {
                return;
            }
            int total = messageMapper.countUserQuestions(userId);
            if (!shouldInfer(state, total)) {
                return;
            }
            List<String> messages = messageMapper.findRecentUserQuestions(userId, recentMessageLimit);
            if (messages.isEmpty()) {
                return;
            }
            InferredSignals signals = inferenceService.infer(messages);
            // 写门在 ProfileService：逐字段达置信才更新，否则保留旧；总会刷新调度元数据用于去抖。
            profileService.recordInference(userId, signals, total);
            log.debug("画像统一推断完成：userId={}，processedMsgCount={}", userId, total);
        } catch (Exception e) {
            log.warn("画像统一推断执行失败：userId={}", userId, e);
        }
    }

    /** 去抖：新消息增量与时间间隔任一不达标则不推断。 */
    private boolean shouldInfer(ProfileInferenceState state, int totalQuestions) {
        if (totalQuestions - state.processedMsgCount() < minNewMessages) {
            return false;
        }
        Instant last = state.lastInferenceAt();
        return last == null || last.isBefore(Instant.now().minus(Duration.ofHours(minIntervalHours)));
    }
}
