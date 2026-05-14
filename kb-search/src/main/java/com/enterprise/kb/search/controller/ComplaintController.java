package com.enterprise.kb.search.controller;

import com.enterprise.kb.common.constants.ComplaintPlanStatus;
import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.util.SecurityUtils;
import com.enterprise.kb.search.dto.ComplaintApprovalRequest;
import com.enterprise.kb.search.dto.ComplaintExecutionResult;
import com.enterprise.kb.search.dto.ComplaintPlanModifyRequest;
import com.enterprise.kb.search.model.Complaint;
import com.enterprise.kb.search.model.ComplaintPlan;
import com.enterprise.kb.search.service.ComplaintEscalationService;
import com.enterprise.kb.search.service.ComplaintExecutorService;
import com.enterprise.kb.search.service.ComplaintPlannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 投诉升级控制器。
 * <p>提供投诉案件创建、计划生成、审批、执行等接口。</p>
 */
@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintEscalationService complaintEscalationService;
    private final ComplaintPlannerService complaintPlannerService;
    private final ComplaintExecutorService complaintExecutorService;

    // ---- 投诉案件 ----

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
     * 为投诉案件生成 AI 处理计划
     *
     * @param complaintId 投诉案件 ID
     * @return 生成的处理计划（状态为 PENDING_REVIEW）
     */
    @PostMapping("/{complaintId}/plan")
    public ResponseEntity<ApiResponse<ComplaintPlan>> generatePlan(
            @PathVariable UUID complaintId) {
        return ResponseEntity.ok(ApiResponse.ok(
                complaintPlannerService.generatePlan(complaintId)));
    }

    // ---- 处理计划审批 ----

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
     * 审批通过处理计划
     *
     * @param planId 计划 ID
     * @param req    审批意见（可选）
     * @return 更新后的计划
     */
    @PostMapping("/plans/{planId}/approve")
    public ResponseEntity<ApiResponse<ComplaintPlan>> approvePlan(
            @PathVariable UUID planId,
            @RequestBody(required = false) ComplaintApprovalRequest req) {
        UUID reviewerId = SecurityUtils.getCurrentUserId();
        String comment = req != null ? req.comment() : null;
        return ResponseEntity.ok(ApiResponse.ok(
                complaintEscalationService.approvePlan(planId, reviewerId, comment)));
    }

    /**
     * 拒绝处理计划
     *
     * @param planId 计划 ID
     * @param req    拒绝原因（可选）
     * @return 更新后的计划
     */
    @PostMapping("/plans/{planId}/reject")
    public ResponseEntity<ApiResponse<ComplaintPlan>> rejectPlan(
            @PathVariable UUID planId,
            @RequestBody(required = false) ComplaintApprovalRequest req) {
        UUID reviewerId = SecurityUtils.getCurrentUserId();
        String comment = req != null ? req.comment() : null;
        return ResponseEntity.ok(ApiResponse.ok(
                complaintEscalationService.rejectPlan(planId, reviewerId, comment)));
    }

    /**
     * 修改处理计划后审批通过。
     * <p>允许审批员调整责任方、补偿类型、补偿金额再批准，请求体中为 null 的字段保持原值。
     * 仅 PENDING_REVIEW 状态的计划可操作。</p>
     *
     * @param planId 计划 ID
     * @param req    修改内容
     * @return 修改并审批后的计划
     */
    @PostMapping("/plans/{planId}/modify-and-approve")
    public ResponseEntity<ApiResponse<ComplaintPlan>> modifyAndApprovePlan(
            @PathVariable UUID planId,
            @RequestBody ComplaintPlanModifyRequest req) {
        UUID reviewerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(
                complaintEscalationService.modifyAndApprovePlan(planId, reviewerId, req)));
    }

    // ---- 执行 ----

    /**
     * 执行审批通过的处理计划。
     * <p>仅 APPROVED 状态的计划可执行。执行成功后投诉状态变为 RESOLVED。</p>
     *
     * @param planId 计划 ID
     * @return 执行结果摘要
     */
    @PostMapping("/plans/{planId}/execute")
    public ResponseEntity<ApiResponse<ComplaintExecutionResult>> executePlan(
            @PathVariable UUID planId) {
        return ResponseEntity.ok(ApiResponse.ok(
                complaintExecutorService.execute(planId)));
    }
}
