package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.ComplaintExecutionResult;

import java.util.UUID;

/**
 * 投诉升级处理计划执行服务。
 * <p>仅允许执行状态为 {@code APPROVED} 的计划，执行过程由 ReactAgent 驱动。</p>
 */
public interface ComplaintExecutorService {

    /**
     * 执行审批通过的投诉处理计划。
     * <p>执行顺序：通知责任方 → 执行补偿 → 记录结案 → 通知用户。
     * 执行成功后投诉状态变为 {@code RESOLVED}，计划状态变为 {@code COMPLETED}。
     * 执行失败时计划状态变为 {@code FAILED}，异常上抛。</p>
     *
     * @param planId 投诉处理计划 ID
     * @return 执行结果（含摘要）
     */
    ComplaintExecutionResult execute(UUID planId);
}
