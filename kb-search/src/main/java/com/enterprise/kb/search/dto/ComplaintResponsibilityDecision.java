package com.enterprise.kb.search.dto;

import com.enterprise.kb.common.constants.ResponsibleParty;

/**
 * 投诉责任认定结果。
 *
 * @param responsibleParty 责任方
 * @param responsibilityReason 责任认定理由
 */
public record ComplaintResponsibilityDecision(
        ResponsibleParty responsibleParty,
        String responsibilityReason
) {}
