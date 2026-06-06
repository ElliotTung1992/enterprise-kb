package com.enterprise.kb.common.constants;

/**
 * 答案语言偏好，用户画像维度之一（ADR-016）。
 */
public enum AnswerLanguage {
    /** 中文作答。 */
    ZH,
    /** 英文作答。 */
    EN,
    /** 跟随提问语言（不强制，由模型按本轮提问语言决定）。 */
    FOLLOW
}
