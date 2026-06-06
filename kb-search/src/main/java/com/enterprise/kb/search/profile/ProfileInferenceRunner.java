package com.enterprise.kb.search.profile;

import com.enterprise.kb.search.dto.SeniorityInference;
import com.enterprise.kb.search.mapper.QaChatMessageMapper;
import com.enterprise.kb.search.service.SeniorityInferenceService;
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
import java.util.Optional;
import java.util.UUID;

/**
 * 用户画像资历推断的执行单元（ADR-016，Phase 2）。
 *
 * <p>一次完整推断：去抖（个性化开关 / 已显式声明 / 新消息增量 / 时间间隔）→ 取近 N 条提问
 * → LLM 推断资历 → 达置信阈值且无显式资历则写入，否则仅刷新调度元数据去抖。</p>
 *
 * <p>被两条路径共用：Kafka 消费者 {@link ProfileInferenceWorker}（正常路径）、
 * 以及 {@link ProfileActivityListener} 的本地线程池降级路径（Kafka 不可用时）。
 * 按用户去抖天然幂等，重复调用安全。任何异常都在内部吞咽（推断是尽力而为的离线增强，
 * 不应让毒丸消息反复重投或拖垮降级线程池）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "enterprise.kb.profile.inference.enabled", havingValue = "true")
public class ProfileInferenceRunner {

    /** 推断取用的近 N 条本人提问。 */
    @Value("${enterprise.kb.profile.inference.recent-message-limit:50}")
    private int recentMessageLimit;
    /** 去抖：距上次推断新消息 ≥ 此值才重算。 */
    @Value("${enterprise.kb.profile.inference.min-new-messages:10}")
    private int minNewMessages;
    /** 去抖：距上次推断间隔 ≥ 此小时数才重算。 */
    @Value("${enterprise.kb.profile.inference.min-interval-hours:6}")
    private long minIntervalHours;
    /** 置信闸：推断资历低于此值不应用，仅刷新调度元数据去抖。 */
    @Value("${enterprise.kb.profile.inference.confidence-threshold:0.7}")
    private double confidenceThreshold;

    private final ProfileService profileService;
    private final QaChatMessageMapper messageMapper;
    private final SeniorityInferenceService seniorityInferenceService;

    /**
     * 对指定用户执行一次（去抖后的）资历推断。
     *
     * @param userId 用户 ID
     */
    public void run(UUID userId) {
        if (userId == null) {
            return;
        }
        try {
            ProfileInferenceState state = profileService.getInferenceState(userId);
            if (!state.personalizationEnabled() || state.hasDeclaredSeniority()) {
                // 个性化关闭，或用户已显式声明资历——推断无意义。
                return;
            }
            int total = messageMapper.countUserQuestions(userId);
            if (!shouldInfer(state, total)) {
                return;
            }
            List<String> questions = messageMapper.findRecentUserQuestions(userId, recentMessageLimit);
            if (questions.isEmpty()) {
                return;
            }
            Optional<SeniorityInference> result = seniorityInferenceService.infer(questions);
            if (result.isPresent() && result.get().confidence() >= confidenceThreshold) {
                SeniorityInference inf = result.get();
                profileService.recordInference(userId, inf.seniority(), inf.confidence(), total);
                log.info("画像资历推断写入：userId={}，seniority={}，confidence={}",
                        userId, inf.seniority(), inf.confidence());
            } else {
                // 未达置信或推断失败：仅刷新调度元数据用于去抖，保留原推断、不应用。
                profileService.recordInference(userId, null, null, total);
                log.debug("画像资历推断未达阈值，跳过应用：userId={}", userId);
            }
        } catch (Exception e) {
            log.warn("画像资历推断执行失败：userId={}", userId, e);
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
