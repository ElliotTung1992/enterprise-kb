package com.enterprise.kb.search.service;

import java.util.UUID;

/**
 * 投诉升级重规划服务。
 * <p>当商家超时或拒绝响应时，从当前计划的 fallback 列表取下一方案生成新计划，
 * 重新进入人工审批流程。若已达最大重规划次数（2 次），则自动升级至高级专员。</p>
 */
public interface ComplaintReplannerService {

    /**
     * 触发重规划。
     * <ul>
     *   <li>若 replanCount &lt; 2：从 fallback[replanCount] 取下一方案，生成新计划（状态 PENDING_REVIEW）。</li>
     *   <li>若 fallback 条目为 ESCALATE_TO_SENIOR 或 replanCount &ge; 2：投诉状态置为 ESCALATED，移交高级专员。</li>
     * </ul>
     *
     * @param planId 当前失败或超时的计划 ID
     */
    void replan(UUID planId);
}
