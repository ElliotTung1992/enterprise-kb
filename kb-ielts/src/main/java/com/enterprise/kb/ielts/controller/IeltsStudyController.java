package com.enterprise.kb.ielts.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.ielts.dto.ReviewRequest;
import com.enterprise.kb.ielts.dto.StartStudyRequest;
import com.enterprise.kb.ielts.dto.StudyPlanItem;
import com.enterprise.kb.ielts.dto.StudyStatsResponse;
import com.enterprise.kb.ielts.dto.TodayPlanResponse;
import com.enterprise.kb.ielts.model.IeltsStudyRecord;
import com.enterprise.kb.ielts.service.IeltsStudyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 雅思学习核心逻辑接口（今日计划、复习提交、统计）
 */
@RestController
@RequestMapping("/api/ielts/study")
@RequiredArgsConstructor
public class IeltsStudyController {

    private final IeltsStudyService studyService;

    /**
     * 获取今日学习计划
     *
     * @return 今日计划信息及待复习项列表
     */
    @GetMapping("/today")
    public ApiResponse<TodayPlanResponse> getTodayPlan() {
        return ApiResponse.ok(studyService.getTodayPlan());
    }

    /**
     * 开始学习某条内容（初始化学习记录）
     *
     * @param request 内容类型 + 内容 ID
     * @return 学习记录
     */
    @PostMapping("/start")
    public ApiResponse<IeltsStudyRecord> startStudying(@Valid @RequestBody StartStudyRequest request) {
        return ApiResponse.ok(studyService.startStudying(request.contentType(), request.contentId()));
    }

    /**
     * 提交本次复习评分（AGAIN / HARD / GOOD / EASY）
     *
     * @param request 学习记录 ID + 评分
     * @return 更新后的学习记录
     */
    @PostMapping("/review")
    public ApiResponse<IeltsStudyRecord> submitReview(@Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok(studyService.submitReview(request));
    }

    /**
     * 获取学习统计数据
     *
     * @return 各状态数量、今日复习次数、连续学习天数
     */
    @GetMapping("/stats")
    public ApiResponse<StudyStatsResponse> getStats() {
        return ApiResponse.ok(studyService.getStats());
    }

    /**
     * 按学习状态查询所有条目列表（含摘要），用于统计页点击展开列表
     *
     * @param status LEARNING / REVIEWING / MASTERED
     * @return 条目列表
     */
    @GetMapping("/records")
    public ApiResponse<List<StudyPlanItem>> getRecordsByStatus(@RequestParam String status) {
        return ApiResponse.ok(studyService.getRecordsByStatus(status));
    }
}
