package com.enterprise.kb.common.constants;

/**
 * 答案风格偏好，用户画像维度之一（ADR-016）。
 */
public enum AnswerStyle {
    /** 直接给结论。 */
    DIRECT,
    /** 带解释：说明推理与依据。 */
    EXPLAINED,
    /** 带示例：尽量给可操作的例子。 */
    WITH_EXAMPLES
}
