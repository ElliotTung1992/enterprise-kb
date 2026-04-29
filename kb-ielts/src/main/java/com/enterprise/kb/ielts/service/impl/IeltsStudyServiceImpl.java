package com.enterprise.kb.ielts.service.impl;

import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.ielts.config.IeltsStudyConfig;
import com.enterprise.kb.ielts.dto.ReviewRequest;
import com.enterprise.kb.ielts.dto.StudyStatsResponse;
import com.enterprise.kb.ielts.dto.TodayPlanResponse;
import com.enterprise.kb.ielts.mapper.IeltsDailyPlanMapper;
import com.enterprise.kb.ielts.mapper.IeltsReviewLogMapper;
import com.enterprise.kb.ielts.mapper.IeltsStudyRecordMapper;
import com.enterprise.kb.ielts.model.IeltsDailyPlan;
import com.enterprise.kb.ielts.model.IeltsReviewLog;
import com.enterprise.kb.ielts.model.IeltsStudyRecord;
import com.enterprise.kb.ielts.service.IeltsStudyService;
import com.enterprise.kb.ielts.study.SpacedRepetitionCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IeltsStudyServiceImpl implements IeltsStudyService {

    private final IeltsStudyRecordMapper recordMapper;
    private final IeltsReviewLogMapper reviewLogMapper;
    private final IeltsDailyPlanMapper dailyPlanMapper;
    private final IeltsStudyConfig studyConfig;

    @Override
    @Transactional
    public TodayPlanResponse getTodayPlan() {
        LocalDate today = LocalDate.now();

        // 获取或创建今日计划
        IeltsDailyPlan plan = dailyPlanMapper.findByPlanDate(today).orElseGet(() -> {
            List<IeltsStudyRecord> due = recordMapper.findDueForReview(today);
            IeltsDailyPlan newPlan = new IeltsDailyPlan();
            newPlan.setId(UUID.randomUUID());
            newPlan.setPlanDate(today);
            newPlan.setTotalItems(due.size());
            newPlan.setCompletedItems(0);
            newPlan.setGeneratedAt(Instant.now());
            dailyPlanMapper.insert(newPlan);
            return newPlan;
        });

        List<IeltsStudyRecord> dueItems = recordMapper.findDueForReview(today);
        return new TodayPlanResponse(today, plan.getTotalItems(), plan.getCompletedItems(), dueItems);
    }

    @Override
    @Transactional
    public IeltsStudyRecord startStudying(String contentType, UUID contentId) {
        return recordMapper.findByContentTypeAndContentId(contentType, contentId)
                .orElseGet(() -> {
                    IeltsStudyRecord record = new IeltsStudyRecord();
                    record.setId(UUID.randomUUID());
                    record.setContentType(contentType);
                    record.setContentId(contentId);
                    record.setStatus("LEARNING");
                    record.setEaseFactor(new BigDecimal("2.50"));
                    record.setIntervalDays(1);
                    record.setRepetitionCount(0);
                    record.setNextReviewAt(LocalDate.now());
                    record.setLastReviewedAt(Instant.now());
                    record.setCreatedAt(Instant.now());
                    recordMapper.insert(record);
                    log.debug("新建学习记录: contentType={}, contentId={}", contentType, contentId);
                    return record;
                });
    }

    @Override
    @Transactional
    public IeltsStudyRecord submitReview(ReviewRequest request) {
        IeltsStudyRecord record = recordMapper.findById(request.recordId())
                .orElseThrow(() -> new ResourceNotFoundException("IeltsStudyRecord", request.recordId()));

        // 应用 SM-2 或简单切换
        SpacedRepetitionCalculator.apply(record, request.rating());
        recordMapper.update(record);

        // 记录复习日志
        IeltsReviewLog reviewLog = new IeltsReviewLog();
        reviewLog.setId(UUID.randomUUID());
        reviewLog.setRecordId(record.getId());
        reviewLog.setRating(request.rating());
        reviewLog.setReviewedAt(Instant.now());
        reviewLogMapper.insert(reviewLog);

        // 更新今日计划完成数
        updateTodayCompleted();

        return record;
    }

    @Override
    @Transactional(readOnly = true)
    public StudyStatsResponse getStats() {
        LocalDate today = LocalDate.now();
        long total = recordMapper.countAll();
        long learning = recordMapper.countByStatus("LEARNING");
        long reviewing = recordMapper.countByStatus("REVIEWING");
        long mastered = recordMapper.countByStatus("MASTERED");
        long todayReviews = reviewLogMapper.countByDate(today);
        int streak = computeStreak();
        return new StudyStatsResponse(total, learning, reviewing, mastered, todayReviews, streak);
    }

    private void updateTodayCompleted() {
        LocalDate today = LocalDate.now();
        dailyPlanMapper.findByPlanDate(today).ifPresent(plan -> {
            long todayReviews = reviewLogMapper.countByDate(today);
            plan.setCompletedItems((int) Math.min(todayReviews, plan.getTotalItems()));
            dailyPlanMapper.update(plan);
        });
    }

    private int computeStreak() {
        List<IeltsDailyPlan> recent = dailyPlanMapper.findRecent(365);
        int streak = 0;
        LocalDate expected = LocalDate.now();
        for (IeltsDailyPlan plan : recent) {
            if (plan.getCompletedItems() > 0 && plan.getPlanDate().equals(expected)) {
                streak++;
                expected = expected.minusDays(1);
            } else {
                break;
            }
        }
        return streak;
    }
}
