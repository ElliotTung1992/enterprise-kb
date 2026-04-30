package com.enterprise.kb.ielts.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 今日学习计划响应
 *
 * @param planDate       计划日期
 * @param totalItems     今日计划总数（到期复习 + 补充新内容）
 * @param completedItems 已完成数
 * @param items          计划条目列表：studyMode=REVIEW 的排在前，NEW 的排在后
 */
public record TodayPlanResponse(
        LocalDate planDate,
        int totalItems,
        int completedItems,
        List<StudyPlanItem> items
) {}
