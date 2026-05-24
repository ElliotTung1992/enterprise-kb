package com.enterprise.kb.search.dto;

import java.util.UUID;

/**
 * 投诉处理计划执行结果
 *
 * @param planId      计划 ID
 * @param complaintId 投诉案件 ID
 * @param summary     执行摘要
 */
public record ComplaintExecutionResult(
        /** 计划 ID */
        UUID planId,
        /** 投诉案件 ID */
        UUID complaintId,
        /** 执行摘要（Agent 生成） */
        String summary
) {}
