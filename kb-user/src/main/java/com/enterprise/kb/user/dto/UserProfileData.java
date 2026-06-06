package com.enterprise.kb.user.dto;

import com.enterprise.kb.common.constants.AnswerLanguage;
import com.enterprise.kb.common.constants.AnswerLength;
import com.enterprise.kb.common.constants.AnswerStyle;
import com.enterprise.kb.common.constants.Seniority;

import java.time.Instant;

/**
 * 用户画像 JSONB 的结构化映射（内部使用，对应 user_profiles.profile 列）。
 *
 * <p>三段式：{@code declared}（用户显式声明，优先级最高）、{@code inferred}（离线统一推断，已过置信闸；
 * 4 维与 declared 对应）、{@code meta}（个性化开关与推断调度元数据）。所有字段允许为空。</p>
 */
public record UserProfileData(Declared declared, Inferred inferred, Meta meta) {

    /** 显式声明层（设置页写入，优先级最高）。 */
    public record Declared(Seniority seniority, AnswerLength answerLength,
                           AnswerLanguage answerLanguage, AnswerStyle answerStyle) {
    }

    /** 离线推断层（统一推断写入，仅存已过置信闸的 4 维值；置信度不入库）。 */
    public record Inferred(Seniority seniority, AnswerLength answerLength,
                           AnswerLanguage answerLanguage, AnswerStyle answerStyle,
                           Instant inferredAt) {
    }

    /** 元数据：个性化总开关 + 推断调度去抖状态。 */
    public record Meta(Boolean personalizationEnabled, Instant lastInferenceAt, Integer processedMsgCount) {
    }

    /** 空画像。 */
    public static UserProfileData empty() {
        return new UserProfileData(null, null, null);
    }

    public Declared declaredOrEmpty() {
        return declared != null ? declared : new Declared(null, null, null, null);
    }

    public Inferred inferredOrEmpty() {
        return inferred != null ? inferred : new Inferred(null, null, null, null, null);
    }

    /** 元数据缺省：个性化默认开启、无推断记录。 */
    public Meta metaOrDefault() {
        if (meta == null) {
            return new Meta(Boolean.TRUE, null, 0);
        }
        return new Meta(
                meta.personalizationEnabled() == null ? Boolean.TRUE : meta.personalizationEnabled(),
                meta.lastInferenceAt(),
                meta.processedMsgCount() == null ? 0 : meta.processedMsgCount());
    }
}
