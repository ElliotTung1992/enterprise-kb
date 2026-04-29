package com.enterprise.kb.ielts.dto;

/**
 * 学习统计数据响应
 *
 * @param totalRecords   总学习记录数
 * @param learningCount  学习中数量
 * @param reviewingCount 复习中数量
 * @param masteredCount  已掌握数量
 * @param todayReviews   今日已复习次数
 * @param streakDays     连续学习天数
 */
public record StudyStatsResponse(
        long totalRecords,
        long learningCount,
        long reviewingCount,
        long masteredCount,
        long todayReviews,
        int streakDays
) {}
