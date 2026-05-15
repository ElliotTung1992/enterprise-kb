package com.enterprise.kb.search.dto;

import com.enterprise.kb.common.constants.ResponsibleParty;

/**
 * 人工判责请求体。
 *
 * @param responsibleParty 人工指定的责任方（不可为 DISPUTED）
 * @param reason           判责理由，供后续审批人参考
 */
public record ComplaintPartyAssignmentRequest(
        ResponsibleParty responsibleParty,
        String reason
) {
}
