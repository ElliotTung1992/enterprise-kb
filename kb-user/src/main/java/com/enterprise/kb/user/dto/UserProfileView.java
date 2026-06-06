package com.enterprise.kb.user.dto;

import com.enterprise.kb.common.constants.AnswerLanguage;
import com.enterprise.kb.common.constants.AnswerLength;
import com.enterprise.kb.common.constants.AnswerStyle;
import com.enterprise.kb.common.constants.Seniority;

/**
 * 用户画像服务态视图（GET 返回），合并显式与推断后的最终值并标注来源。
 *
 * @param seniority              服务态资历（显式优先；显式为空时取置信达标的推断值，否则 null）
 * @param senioritySource        资历来源：EXPLICIT / INFERRED / null
 * @param seniorityConfidence    资历为推断来源时的置信度，否则 null
 * @param answerLength           答案长度偏好（仅显式）
 * @param answerLanguage         答案语言偏好（仅显式）
 * @param answerStyle            答案风格偏好（仅显式）
 * @param personalizationEnabled 个性化总开关
 */
public record UserProfileView(
        Seniority seniority,
        String senioritySource,
        Double seniorityConfidence,
        AnswerLength answerLength,
        AnswerLanguage answerLanguage,
        AnswerStyle answerStyle,
        boolean personalizationEnabled) {
}
