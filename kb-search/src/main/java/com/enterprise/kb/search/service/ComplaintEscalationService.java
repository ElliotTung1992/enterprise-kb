package com.enterprise.kb.search.service;

import com.enterprise.kb.common.constants.ComplaintPlanStatus;
import com.enterprise.kb.common.constants.ComplaintStatus;
import com.enterprise.kb.search.model.Complaint;
import com.enterprise.kb.search.model.ComplaintPlan;

import java.util.List;
import java.util.UUID;

/**
 * 投诉升级基础数据服务接口
 */
public interface ComplaintEscalationService {

    /**
     * 创建投诉升级案件
     *
     * @param userId  投诉用户 ID
     * @param orderId 关联订单号
     * @param content 投诉内容
     * @return 创建后的投诉案件
     */
    Complaint createComplaint(UUID userId, String orderId, String content);

    /**
     * 根据 ID 查询投诉案件
     *
     * @param id 投诉案件 ID
     * @return 投诉案件
     */
    Complaint findComplaintById(UUID id);

    /**
     * 保存投诉处理计划
     *
     * @param plan 投诉处理计划
     * @return 保存后的处理计划
     */
    ComplaintPlan savePlan(ComplaintPlan plan);

    /**
     * 根据 ID 查询投诉处理计划
     *
     * @param id 计划 ID
     * @return 投诉处理计划
     */
    ComplaintPlan findPlanById(UUID id);

    /**
     * 查询投诉案件下的全部处理计划
     *
     * @param complaintId 投诉案件 ID
     * @return 处理计划列表
     */
    List<ComplaintPlan> listPlans(UUID complaintId);

    /**
     * 更新投诉案件状态
     *
     * @param id     投诉案件 ID
     * @param status 新状态
     */
    void updateComplaintStatus(UUID id, ComplaintStatus status);

    /**
     * 更新投诉处理计划状态
     *
     * @param id     计划 ID
     * @param status 新状态
     */
    void updatePlanStatus(UUID id, ComplaintPlanStatus status);
}
