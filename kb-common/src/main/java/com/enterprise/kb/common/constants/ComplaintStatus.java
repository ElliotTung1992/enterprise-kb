package com.enterprise.kb.common.constants;

/**
 * 投诉升级案件状态。
 */
public enum ComplaintStatus {
    /** 已创建投诉案件，尚未进入计划生成 */
    OPEN,
    /** 正在收集数据并生成处理计划 */
    PLANNING,
    /** 已生成处理计划，等待人工审核 */
    PENDING_REVIEW,
    /** 处理计划已通过审核，正在执行 */
    EXECUTING,
    /** 投诉已处理完成 */
    RESOLVED,
    /** 已升级给高级专员处理 */
    ESCALATED,
    /** 投诉已关闭 */
    CLOSED
}
