package com.enterprise.kb.common.constants;

/**
 * 答案长度偏好，用户画像维度之一（ADR-016）。
 */
public enum AnswerLength {
    /** 简洁：直接给要点，尽量短。 */
    CONCISE,
    /** 适中：默认详略。 */
    MEDIUM,
    /** 详细：展开解释与背景。 */
    DETAILED
}
