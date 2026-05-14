package com.enterprise.kb.search.service;

import com.enterprise.kb.search.model.ComplaintPlan;

import java.util.UUID;

/**
 * 投诉升级 Planner 服务接口
 */
public interface ComplaintPlannerService {

    /**
     * 为投诉案件生成待审核处理计划。
     *
     * @param complaintId 投诉案件 ID
     * @return 已落库的处理计划
     */
    ComplaintPlan generatePlan(UUID complaintId);
}
