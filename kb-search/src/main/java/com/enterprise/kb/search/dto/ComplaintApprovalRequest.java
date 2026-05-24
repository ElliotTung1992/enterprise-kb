package com.enterprise.kb.search.dto;

/**
 * 投诉处理计划审批请求
 *
 * @param comment 审批意见
 */
public record ComplaintApprovalRequest(
        /** 审批意见（可选） */
        String comment
) {}
