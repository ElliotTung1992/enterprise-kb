package com.enterprise.kb.search.service;

import com.enterprise.kb.common.constants.ComplaintPlanStatus;
import com.enterprise.kb.common.constants.ComplaintStatus;
import com.enterprise.kb.search.dto.ComplaintPlanModifyRequest;
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

    /**
     * 审批通过投诉处理计划
     *
     * @param planId     计划 ID
     * @param reviewerId 审批人 ID
     * @param comment    审批意见（可选）
     * @return 更新后的计划
     */
    ComplaintPlan approvePlan(UUID planId, UUID reviewerId, String comment);

    /**
     * 拒绝投诉处理计划
     *
     * @param planId     计划 ID
     * @param reviewerId 审批人 ID
     * @param comment    拒绝原因（可选）
     * @return 更新后的计划
     */
    ComplaintPlan rejectPlan(UUID planId, UUID reviewerId, String comment);

    /**
     * 查询指定状态的处理计划列表
     *
     * @param status 计划状态，为 null 时返回全部
     * @return 处理计划列表，按创建时间降序
     */
    List<ComplaintPlan> findPlansByStatus(ComplaintPlanStatus status);

    /**
     * 修改处理计划字段并审批通过。
     * <p>仅允许在 PENDING_REVIEW 状态下执行。请求体中为 null 的字段保持原值不变。</p>
     *
     * @param planId     计划 ID
     * @param reviewerId 审批人 ID
     * @param req        修改内容（字段为 null 则不修改）
     * @return 修改并审批后的计划
     */
    ComplaintPlan modifyAndApprovePlan(UUID planId, UUID reviewerId, ComplaintPlanModifyRequest req);
}
