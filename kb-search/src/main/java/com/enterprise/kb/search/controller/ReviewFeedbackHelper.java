package com.enterprise.kb.search.controller;

import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata.ToolFeedback;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata.ToolFeedback.FeedbackResult;
import com.enterprise.kb.common.exception.KbException;
import com.enterprise.kb.search.model.ReviewRequest;
import org.springframework.http.HttpStatus;

/**
 * HITL 审核反馈构建工具（包级私有）。
 * <p>消除 {@link CustomerAssistantController} 与 {@link ReviewController} 之间的
 * {@link InterruptionMetadata} 构建重复逻辑。</p>
 */
final class ReviewFeedbackHelper {

    private ReviewFeedbackHelper() {}

    /**
     * 根据审核申请和决策结果构建 Agent 恢复所需的中断元数据。
     *
     * @param reviewReq 审核申请（必须含有效的 toolCallId）
     * @param result    审核决策（APPROVED / REJECTED）
     * @return 用于 {@code RunnableConfig.addHumanFeedback()} 的中断元数据
     */
    static InterruptionMetadata buildFeedback(ReviewRequest reviewReq, FeedbackResult result) {
        if (reviewReq.getToolCallId() == null) {
            throw new KbException("审核申请缺少 toolCallId，无法恢复 Agent 执行", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        ToolFeedback toolFeedback = ToolFeedback.builder()
                .id(reviewReq.getToolCallId())
                .name(reviewReq.getToolCallName())
                .result(result)
                .build();
        return new InterruptionMetadata.Builder().addToolFeedback(toolFeedback).build();
    }
}
