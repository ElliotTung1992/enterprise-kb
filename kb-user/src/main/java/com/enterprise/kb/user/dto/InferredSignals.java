package com.enterprise.kb.user.dto;

import com.enterprise.kb.common.constants.AnswerLanguage;
import com.enterprise.kb.common.constants.AnswerLength;
import com.enterprise.kb.common.constants.AnswerStyle;
import com.enterprise.kb.common.constants.Seniority;

/**
 * 一次离线统一推断的原始输出（4 维各 value+confidence，瞬时、不入库）。
 *
 * <p>由推断服务产出，交给 {@code ProfileService.recordInference} 逐字段过置信闸后写入 inferred 层。
 * 某维无信号则该维 value=null（不更新该维）。</p>
 */
public record InferredSignals(
        Seniority seniority, Double seniorityConfidence,
        AnswerLength answerLength, Double answerLengthConfidence,
        AnswerLanguage answerLanguage, Double answerLanguageConfidence,
        AnswerStyle answerStyle, Double answerStyleConfidence) {

    /** 全空（无任何推断信号）。 */
    public static InferredSignals empty() {
        return new InferredSignals(null, null, null, null, null, null, null, null);
    }
}
