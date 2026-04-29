package com.enterprise.kb.ielts.dto;

import com.enterprise.kb.ielts.model.IeltsStudyRecord;

import java.time.LocalDate;
import java.util.List;

/**
 * 今日学习计划响应
 *
 * @param planDate       计划日期
 * @param totalItems     今日计划总数
 * @param completedItems 已完成数
 * @param dueItems       待复习/学习的记录列表
 */
public record TodayPlanResponse(
        LocalDate planDate,
        int totalItems,
        int completedItems,
        List<IeltsStudyRecord> dueItems
) {}
