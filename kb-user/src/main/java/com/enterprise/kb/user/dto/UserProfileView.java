package com.enterprise.kb.user.dto;

import com.enterprise.kb.common.constants.AnswerLanguage;
import com.enterprise.kb.common.constants.AnswerLength;
import com.enterprise.kb.common.constants.AnswerStyle;
import com.enterprise.kb.common.constants.Seniority;

/**
 * 用户画像服务态视图（GET 返回）：每维合并显式与推断后的最终值 + 来源（EXPLICIT/INFERRED/null）。
 *
 * @param seniority              服务态资历（显式优先，否则取推断）
 * @param senioritySource        资历来源：EXPLICIT / INFERRED / null
 * @param answerLength           服务态答案长度
 * @param answerLengthSource     长度来源
 * @param answerLanguage         服务态答案语言
 * @param answerLanguageSource   语言来源
 * @param answerStyle            服务态答案风格
 * @param answerStyleSource      风格来源
 * @param personalizationEnabled 个性化总开关
 */
public record UserProfileView(
        Seniority seniority, String senioritySource,
        AnswerLength answerLength, String answerLengthSource,
        AnswerLanguage answerLanguage, String answerLanguageSource,
        AnswerStyle answerStyle, String answerStyleSource,
        boolean personalizationEnabled) {
}
