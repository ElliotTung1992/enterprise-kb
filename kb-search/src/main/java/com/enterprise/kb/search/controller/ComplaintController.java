package com.enterprise.kb.search.controller;

import com.enterprise.kb.common.constants.ComplaintPlanStatus;
import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.util.SecurityUtils;
import com.enterprise.kb.search.dto.ComplaintApprovalRequest;
import com.enterprise.kb.search.dto.ComplaintPartyAssignmentRequest;
import com.enterprise.kb.search.dto.ComplaintPlanModifyRequest;
import com.enterprise.kb.search.model.Complaint;
import com.enterprise.kb.search.model.ComplaintPlan;
import com.enterprise.kb.search.service.ComplaintEscalationService;
import com.enterprise.kb.search.service.ComplaintWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 投诉升级控制器。
 * <p>提供投诉案件创建、工作流规划、HITL 审批、重规划等接口。
 * 规划与执行由 {@link ComplaintWorkflowService} 通过 StateGraph 工作流编排。</p>
 */
@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintEscalationService complaintEscalationService;
    private final ComplaintWorkflowService complaintWorkflowService;

    /**
     * 查询投诉案件详情
     *
     * @param complaintId 投诉案件 ID
     * @return 投诉案件
     */
    @GetMapping("/{complaintId}")
    public ResponseEntity<ApiResponse<Complaint>> getComplaint(
            @PathVariable UUID complaintId) {
        return ResponseEntity.ok(ApiResponse.ok(
                complaintEscalationService.findComplaintById(complaintId)));
    }

    /**
     * 创建投诉升级案件
     *
     * @param orderId 关联订单号
     * @param content 投诉内容
     * @return 创建后的投诉案件
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Complaint>> createComplaint(
            @RequestParam String orderId,
            @RequestParam String content) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(
                complaintEscalationService.createComplaint(userId, orderId, content)));
    }

    /**
     * 启动投诉处理工作流（AI 规划 + 等待审批）。
     * <p>工作流按 collectData → applyRules → [llmInference →] savePlan 顺序执行。
     * 若 LLM 判定为 DISPUTED，在 humanAssignParty 节点暂停，返回 AWAITING_RESPONSIBILITY 计划；
     * 否则在 applyDecision 节点暂停，返回 PENDING_REVIEW 计划，等待人工审批。</p>
     *
     * @param complaintId 投诉案件 ID
     * @return 生成的处理计划（状态为 PENDING_REVIEW）
     */
    @PostMapping("/{complaintId}/plan")
    public ResponseEntity<ApiResponse<ComplaintPlan>> startWorkflow(
            @PathVariable UUID complaintId) {
        return ResponseEntity.ok(ApiResponse.ok(
                complaintWorkflowService.startPlanning(complaintId)));
    }

    /**
     * 查询处理计划列表（按状态过滤）
     *
     * @param status 计划状态，不传则返回全部
     * @return 处理计划列表，按创建时间降序
     */
    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<ComplaintPlan>>> listPlans(
            @RequestParam(required = false) ComplaintPlanStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(
                complaintEscalationService.findPlansByStatus(status)));
    }

    /**
     * 查询指定投诉案件下的所有处理计划
     *
     * @param complaintId 投诉案件 ID
     * @return 处理计划列表
     */
    @GetMapping("/{complaintId}/plans")
    public ResponseEntity<ApiResponse<List<ComplaintPlan>>> listPlansByComplaint(
            @PathVariable UUID complaintId) {
        return ResponseEntity.ok(ApiResponse.ok(
                complaintEscalationService.listPlans(complaintId)));
    }

    /**
     * LLM 判定为 DISPUTED 后，人工指定责任方并恢复工作流。
     * <p>工作流从 humanAssignParty 节点继续，经 savePlan 生成完整计划后再次暂停于 applyDecision。</p>
     *
     * @param planId 计划 ID
     * @param req    责任方与判责理由
     * @return 已生成的处理计划（状态为 PENDING_REVIEW）
     */
    @PostMapping("/plans/{planId}/assign-party")
    public ResponseEntity<ApiResponse<ComplaintPlan>> assignParty(
            @PathVariable UUID planId,
            @RequestBody ComplaintPartyAssignmentRequest req) {
        UUID assignerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(
                complaintWorkflowService.resumeWithPartyAssignment(planId, assignerId, req.responsibleParty(), req.reason())));
    }

    /**
     * 审批通过处理计划，工作流自动恢复并执行补偿方案。
     *
     * @param planId 计划 ID
     * @param req    审批意见（可选）
     * @return 执行后的计划
     */
    @PostMapping("/plans/{planId}/approve")
    public ResponseEntity<ApiResponse<ComplaintPlan>> approvePlan(
            @PathVariable UUID planId,
            @RequestBody(required = false) ComplaintApprovalRequest req) {
        UUID reviewerId = SecurityUtils.getCurrentUserId();
        String comment = req != null ? req.comment() : null;
        return ResponseEntity.ok(ApiResponse.ok(
                complaintWorkflowService.resumeApproved(planId, reviewerId, comment)));
    }

    /**
     * 拒绝处理计划，工作流恢复并关闭投诉案件。
     *
     * @param planId 计划 ID
     * @param req    拒绝原因（可选）
     * @return 拒绝后的计划
     */
    @PostMapping("/plans/{planId}/reject")
    public ResponseEntity<ApiResponse<ComplaintPlan>> rejectPlan(
            @PathVariable UUID planId,
            @RequestBody(required = false) ComplaintApprovalRequest req) {
        UUID reviewerId = SecurityUtils.getCurrentUserId();
        String comment = req != null ? req.comment() : null;
        return ResponseEntity.ok(ApiResponse.ok(
                complaintWorkflowService.resumeRejected(planId, reviewerId, comment)));
    }

    /**
     * 修改处理计划字段后审批通过，工作流自动恢复并执行修改后的方案。
     *
     * @param planId 计划 ID
     * @param req    修改内容
     * @return 执行后的计划
     */
    @PostMapping("/plans/{planId}/modify-and-approve")
    public ResponseEntity<ApiResponse<ComplaintPlan>> modifyAndApprovePlan(
            @PathVariable UUID planId,
            @RequestBody ComplaintPlanModifyRequest req) {
        UUID reviewerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(
                complaintWorkflowService.resumeModified(planId, reviewerId, req)));
    }

    /**
     * 手动触发计划超时重规划。
     * <p>从 fallback 列表取下一方案创建新计划并进入审批；
     * 若已达最大重规划次数则升级至高级专员。</p>
     *
     * @param planId 计划 ID
     * @return 操作成功响应
     */
    @PostMapping("/plans/{planId}/trigger-timeout")
    public ResponseEntity<ApiResponse<Void>> triggerTimeout(
            @PathVariable UUID planId) {
        complaintWorkflowService.triggerReplan(planId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
