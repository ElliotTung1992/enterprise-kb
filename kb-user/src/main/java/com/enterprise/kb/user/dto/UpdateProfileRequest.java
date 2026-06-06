package com.enterprise.kb.user.dto;

import com.enterprise.kb.common.constants.AnswerLanguage;
import com.enterprise.kb.common.constants.AnswerLength;
import com.enterprise.kb.common.constants.AnswerStyle;
import com.enterprise.kb.common.constants.Seniority;

/**
 * 更新用户画像显式声明的请求体（PUT 语义：整体替换 declared 各字段，null 表示清空该字段）。
 *
 * @param seniority              资历（null 清空 → 回落到推断）
 * @param answerLength           答案长度偏好（null 清空 → 系统默认）
 * @param answerLanguage         答案语言偏好（null 清空 → 跟随提问）
 * @param answerStyle            答案风格偏好（null 清空 → 系统默认）
 * @param personalizationEnabled 个性化总开关（null 表示不修改当前值）
 */
public record UpdateProfileRequest(
        Seniority seniority,
        AnswerLength answerLength,
        AnswerLanguage answerLanguage,
        AnswerStyle answerStyle,
        Boolean personalizationEnabled) {
}
