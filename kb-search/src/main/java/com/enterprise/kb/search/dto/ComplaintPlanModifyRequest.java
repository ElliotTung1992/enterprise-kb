package com.enterprise.kb.search.dto;

import com.enterprise.kb.common.constants.CompensationType;
import com.enterprise.kb.common.constants.ResponsibleParty;

import java.math.BigDecimal;

/**
 * 修改投诉处理计划并审批通过的请求体。
 * <p>所有字段均为可选，仅传入需要修改的字段，不传则保持原值。</p>
 */
public record ComplaintPlanModifyRequest(
        /** 修改后的责任方，为 null 则保持原值 */
        ResponsibleParty responsibleParty,
        /** 修改后的补偿类型，为 null 则保持原值 */
        CompensationType compensationType,
        /** 修改后的补偿金额，为 null 则保持原值 */
        BigDecimal compensationAmount,
        /** 审批意见或修改说明（可选） */
        String comment
) {}
