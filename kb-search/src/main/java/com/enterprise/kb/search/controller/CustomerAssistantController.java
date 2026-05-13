package com.enterprise.kb.search.controller;

import com.alibaba.cloud.ai.graph.action.InterruptionMetadata.ToolFeedback.FeedbackResult;
import com.enterprise.kb.common.constants.ReviewStatus;
import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.util.SecurityUtils;
import com.enterprise.kb.search.dto.CustomerAssistantRequest;
import com.enterprise.kb.search.dto.CustomerAssistantResponse;
import com.enterprise.kb.search.dto.CustomerMessageDto;
import com.enterprise.kb.search.dto.CustomerSessionDto;
import com.enterprise.kb.search.dto.ReviewDecisionRequest;
import com.enterprise.kb.search.model.ReviewRequest;
import com.enterprise.kb.search.service.CustomerAssistantService;
import com.enterprise.kb.search.service.ReviewRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 商城客户助手控制器
 * <p>面向 C 端用户，提供售后咨询对话接口及会话管理接口。
 * 与知识库问答（/qa）完全独立，不绑定知识空间。</p>
 */
@RestController
@RequestMapping("/api/v1/after-sales")
@RequiredArgsConstructor
public class CustomerAssistantController {

    private final CustomerAssistantService customerAssistantService;
    private final ReviewRequestService reviewRequestService;

    /**
     * 发送消息
     * <p>sessionId 为 null 时自动创建新会话。
     * 若触发 HITL 审核中断，立即返回"申请已提交"响应。</p>
     *
     * @param req 对话请求（含可选的 sessionId 和消息内容）
     * @return AI 回复及会话 ID
     */
    @PostMapping("/ask")
    public ResponseEntity<ApiResponse<CustomerAssistantResponse>> ask(
            @RequestBody CustomerAssistantRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(customerAssistantService.chat(req)));
    }

    /**
     * 查询当前用户的会话列表
     *
     * @return 会话列表（按最后活跃时间倒序，含消息数）
     */
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<CustomerSessionDto>>> listSessions() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(customerAssistantService.listSessions(userId)));
    }

    /**
     * 查询指定会话的历史消息
     *
     * @param sessionId 会话 ID
     * @return 消息列表（按时间升序）
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ApiResponse<List<CustomerMessageDto>>> getMessages(
            @PathVariable UUID sessionId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(
                customerAssistantService.getMessages(sessionId, userId)));
    }

    /**
     * 软删除会话
     *
     * @param sessionId 会话 ID
     * @return 操作结果
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(@PathVariable UUID sessionId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        customerAssistantService.deleteSession(sessionId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ---- 审核员接口 ----

    /**
     * 查询审核申请列表（审核员视图）
     *
     * @param status 状态过滤：PENDING / APPROVED / REJECTED，不传则返回全部
     * @return 审核申请列表，按创建时间降序
     */
    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<List<ReviewRequest>>> listReviews(
            @RequestParam(required = false) ReviewStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(reviewRequestService.listByStatus(status)));
    }

    /**
     * 查询所有待审核的客户助手申请（审核员视图，兼容旧调用）
     *
     * @return 所有 PENDING 状态的审核申请列表
     */
    @GetMapping("/reviews/pending")
    public ResponseEntity<ApiResponse<List<ReviewRequest>>> listPendingReviews() {
        return ResponseEntity.ok(ApiResponse.ok(reviewRequestService.listByStatus(ReviewStatus.PENDING)));
    }

    /**
     * 批准客户助手售后申请
     * <p>先调用 LLM 恢复 Agent（外部调用），成功后再提交 DB 审核决策。
     * 若 LLM 调用失败则异常上抛，DB 不更新，申请仍为 PENDING 状态，可重试。</p>
     *
     * @param id  审核申请 ID
     * @param req 审核意见（可选）
     * @return Agent 恢复执行后的响应
     */
    @PostMapping("/reviews/{id}/approve")
    public ResponseEntity<ApiResponse<CustomerAssistantResponse>> approveReview(
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
     * 拒绝客户助手售后申请
     *
     * @param id  审核申请 ID
     * @param req 拒绝原因（可选）
     * @return Agent 恢复执行后的响应
     */
    @PostMapping("/reviews/{id}/reject")
    public ResponseEntity<ApiResponse<CustomerAssistantResponse>> rejectReview(
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
