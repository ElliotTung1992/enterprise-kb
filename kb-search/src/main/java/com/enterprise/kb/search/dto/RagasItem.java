package com.enterprise.kb.search.dto;

import java.util.List;
import java.util.UUID;

/**
 * Ragas 单条评估输入。
 *
 * @param caseId      评估用例 ID
 * @param question    问题
 * @param answer      实际回答
 * @param contexts    答案依据文本
 * @param groundTruth 金标答案
 */
public record RagasItem(
        UUID caseId,
        String question,
        String answer,
        List<String> contexts,
        String groundTruth
) {}
