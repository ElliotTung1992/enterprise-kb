package com.enterprise.kb.search.controller;

import com.alibaba.cloud.ai.graph.action.InterruptionMetadata.ToolFeedback.FeedbackResult;
import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.util.SecurityUtils;
import com.enterprise.kb.search.dto.CustomerAssistantResponse;
import com.enterprise.kb.search.dto.ReviewDecisionRequest;
import com.enterprise.kb.search.model.ReviewRequest;
import com.enterprise.kb.search.service.CustomerAssistantService;
import com.enterprise.kb.search.service.ReviewRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 售后审核控制器（管理后台）
 * <p>提供审核员查看待处理申请、批准或拒绝申请的接口。
 * 审核决策通过 {@link CustomerAssistantService#resumeWithFeedback} 恢复被 HITL 中断的 Agent，
 * 审核结果自动写入对应客户助手会话的消息历史。
 * 所有接口需要知识空间 EDITOR 或 OWNER 级别权限。</p>
 */
@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewRequestService reviewRequestService;
    private final CustomerAssistantService customerAssistantService;

    /**
     * 查询待审核申请列表
     * <p>返回指定知识空间下所有状态为 PENDING 的审核申请，按提交时间升序排列。</p>
     *
     * @param spaceId 知识空间 ID
     * @return 待审核申请列表
     */
    @GetMapping("/pending")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'EDITOR')")
    public ResponseEntity<ApiResponse<List<ReviewRequest>>> listPending(@PathVariable UUID spaceId) {
        return ResponseEntity.ok(ApiResponse.ok(reviewRequestService.listPending(spaceId)));
    }

    /**
     * 批准审核申请
     * <p>将申请状态更新为 APPROVED，并恢复被 HITL 中断的客户助手 Agent 继续执行，
     * 生成最终答复写入会话历史供用户查看。</p>
     *
     * @param spaceId 知识空间 ID
     * @param id      审核申请 ID
     * @param req     审核意见（可选）
     * @return Agent 恢复执行后的响应
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'EDITOR')")
    public ResponseEntity<ApiResponse<CustomerAssistantResponse>> approve(
            @PathVariable UUID spaceId,
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewDecisionRequest req) {
        UUID reviewerId = SecurityUtils.getCurrentUserId();
        String comment = req != null ? req.comment() : null;
        ReviewRequest reviewReq = reviewRequestService.findById(id);
        CustomerAssistantResponse response = customerAssistantService.resumeWithFeedback(
                reviewReq.getSessionId(), ReviewFeedbackHelper.buildFeedback(reviewReq, FeedbackResult.APPROVED));
        reviewRequestService.approve(id, reviewerId, comment);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 拒绝审核申请
     * <p>将申请状态更新为 REJECTED，并恢复被 HITL 中断的客户助手 Agent 继续执行，
     * 生成拒绝通知写入会话历史供用户查看。</p>
     *
     * @param spaceId 知识空间 ID
     * @param id      审核申请 ID
     * @param req     拒绝原因
     * @return Agent 恢复执行后的响应
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'EDITOR')")
    public ResponseEntity<ApiResponse<CustomerAssistantResponse>> reject(
            @PathVariable UUID spaceId,
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewDecisionRequest req) {
        UUID reviewerId = SecurityUtils.getCurrentUserId();
        String comment = req != null ? req.comment() : null;

        ReviewRequest reviewReq = reviewRequestService.findById(id);
        CustomerAssistantResponse response = customerAssistantService.resumeWithFeedback(
                reviewReq.getSessionId(), ReviewFeedbackHelper.buildFeedback(reviewReq, FeedbackResult.REJECTED));
        reviewRequestService.reject(id, reviewerId, comment);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

}
