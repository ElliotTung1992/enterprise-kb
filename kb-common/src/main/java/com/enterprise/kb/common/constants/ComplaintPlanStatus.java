package com.enterprise.kb.common.constants;

/**
 * 投诉处理计划状态。
 */
public enum ComplaintPlanStatus {
    /** LLM 无法判定责任方（DISPUTED），等待人工指定责任方 */
    AWAITING_RESPONSIBILITY,
    /** 计划已生成，等待人工审核 */
    PENDING_REVIEW,
    /** 计划已通过人工审核 */
    APPROVED,
    /** 计划被人工拒绝 */
    REJECTED,
    /** 计划正在执行 */
    EXECUTING,
    /** 计划执行失败，后续可进入重规划或升级 */
    FAILED,
    /** 计划执行完成 */
    COMPLETED
}
