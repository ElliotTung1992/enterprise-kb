package com.enterprise.kb.ielts.service;

import com.enterprise.kb.ielts.dto.ReviewRequest;
import com.enterprise.kb.ielts.dto.StudyPlanItem;
import com.enterprise.kb.ielts.dto.StudyStatsResponse;
import com.enterprise.kb.ielts.dto.TodayPlanResponse;
import com.enterprise.kb.ielts.model.IeltsStudyRecord;

import java.util.List;
import java.util.UUID;

/**
 * 雅思学习核心逻辑 Service 接口
 */
public interface IeltsStudyService {

    /**
     * 获取今日学习计划（含待复习列表）
     *
     * @return 今日计划与待复习项
     */
    TodayPlanResponse getTodayPlan();

    /**
     * 初始化或获取某条内容的学习记录
     *
     * @param contentType 内容类型
     * @param contentId   内容 ID
     * @return 学习记录
     */
    IeltsStudyRecord startStudying(String contentType, UUID contentId);

    /**
     * 提交本次复习评分并更新 SM-2 参数
     *
     * @param request 复习请求（recordId + rating）
     * @return 更新后的学习记录
     */
    IeltsStudyRecord submitReview(ReviewRequest request);

    /**
     * 获取学习统计数据
     *
     * @return 各状态数量、今日复习次数、连续天数
     */
    StudyStatsResponse getStats();

    /**
     * 按学习状态查询所有条目（含摘要），用于统计页列表展示
     *
     * @param status LEARNING / REVIEWING / MASTERED
     * @return 学习条目列表
     */
    List<StudyPlanItem> getRecordsByStatus(String status);
}
