package com.enterprise.kb.user.dto;

import java.time.Instant;

/**
 * 离线推断调度所需的画像状态快照（供 Phase 2 worker 去抖判断）。
 *
 * @param hasDeclaredSeniority   用户是否已显式声明资历（已声明则推断无意义，应跳过）
 * @param personalizationEnabled 个性化总开关（关则不推断）
 * @param lastInferenceAt        上次推断时间（用于时间间隔去抖，可为 null）
 * @param processedMsgCount      上次推断时已处理的用户消息数（用于新消息增量去抖）
 */
public record ProfileInferenceState(
        boolean hasDeclaredSeniority,
        boolean personalizationEnabled,
        Instant lastInferenceAt,
        int processedMsgCount) {
}
