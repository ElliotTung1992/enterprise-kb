package com.enterprise.kb.search.model;

import com.enterprise.kb.common.constants.ReviewStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class ReviewRequest {

    private UUID id;
    /** 关联的 QA 会话 ID（同时作为 ReactAgent threadId） */
    private UUID sessionId;
    /** 所属知识空间 ID */
    private UUID spaceId;
    /** 提交申请的用户 ID */
    private UUID userId;
    /** 用户订单号 */
    private String orderId;
    /** AI 提炼的用户诉求 */
    private String reason;
    /** 申请提交时的对话快照（JSONB） */
    private String conversationSnapshot;
    /** simple-shop 查询到的订单详情（JSONB） */
    private String orderDetails;
    /** LLM 发起的工具调用 ID，HITL 中断时由框架生成，resume 时用于重建 InterruptionMetadata */
    private String toolCallId;
    /** LLM 发起的工具名称（固定为 submitAfterSalesReview） */
    private String toolCallName;
    /** 审核状态 */
    private ReviewStatus status;
    /** 审核员用户 ID */
    private UUID reviewerId;
    /** 审核员备注 */
    private String reviewerComment;
    /** 创建时间 */
    private Instant createdAt;
    /** 最后更新时间 */
    private Instant updatedAt;
    /** 审核决策时间 */
    private Instant decidedAt;
}
