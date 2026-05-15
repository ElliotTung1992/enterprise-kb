package com.enterprise.kb.search.service;

import com.enterprise.kb.common.constants.ResponsibleParty;
import com.enterprise.kb.search.dto.ComplaintPlanModifyRequest;
import com.enterprise.kb.search.model.ComplaintPlan;

import java.util.UUID;

/**
 * 投诉处理工作流服务接口。
 * <p>使用 Spring AI Alibaba StateGraph 工作流编排整个投诉处理生命周期：
 * 数据收集 → 规则判定 → LLM 推理（可选）→ 生成计划 → HITL 审批 → 执行/关闭。</p>
 */
public interface ComplaintWorkflowService {

    /**
     * 启动投诉处理工作流。
     * <p>运行 collectData → applyRules → [llmInference →] savePlan 后暂停，等待人工审批。
     * 工作流以 planId 为线程 ID 持久化到 Redis。</p>
     *
     * @param complaintId 投诉案件 ID
     * @return 生成的处理计划（状态为 PENDING_REVIEW）
     */
    ComplaintPlan startPlanning(UUID complaintId);

    /**
     * 审批通过后恢复工作流，自动执行处理计划。
     *
     * @param planId     计划 ID
     * @param reviewerId 审批人 ID
     * @param comment    审批意见（可选）
     * @return 执行后的处理计划
     */
    ComplaintPlan resumeApproved(UUID planId, UUID reviewerId, String comment);

    /**
     * 拒绝后恢复工作流，关闭投诉案件。
     *
     * @param planId     计划 ID
     * @param reviewerId 审批人 ID
     * @param comment    拒绝原因（可选）
     * @return 拒绝后的处理计划
     */
    ComplaintPlan resumeRejected(UUID planId, UUID reviewerId, String comment);

    /**
     * 修改并审批通过后恢复工作流，自动执行修改后的计划。
     *
     * @param planId     计划 ID
     * @param reviewerId 审批人 ID
     * @param req        修改内容
     * @return 执行后的处理计划
     */
    ComplaintPlan resumeModified(UUID planId, UUID reviewerId, ComplaintPlanModifyRequest req);

    /**
     * LLM 判定为 DISPUTED 后，人工指定责任方并恢复工作流。
     * <p>工作流从 humanAssignParty 节点继续，经 savePlan 生成完整计划后再次暂停于 applyDecision，
     * 等待最终审批。</p>
     *
     * @param planId 计划 ID（同时也是工作流 threadId）
     * @param party  人工指定的责任方，不可为 DISPUTED
     * @param reason 判责理由
     * @return 已生成的处理计划（状态为 PENDING_REVIEW）
     */
    ComplaintPlan resumeWithPartyAssignment(UUID planId, UUID assignerId, ResponsibleParty party, String reason);

    /**
     * 触发超时重规划。
     * <p>将当前计划标记为 FAILED，从 fallback 列表取下一方案创建新计划并进入审批；
     * 若已达最大重规划次数则升级至高级专员。</p>
     *
     * @param planId 计划 ID
     */
    void triggerReplan(UUID planId);
}
