package com.enterprise.kb.ielts.service.impl;

import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.ielts.config.IeltsStudyConfig;
import com.enterprise.kb.ielts.dto.ReviewRequest;
import com.enterprise.kb.ielts.dto.StudyPlanItem;
import com.enterprise.kb.ielts.dto.StudyStatsResponse;
import com.enterprise.kb.ielts.dto.TodayPlanResponse;
import com.enterprise.kb.ielts.mapper.*;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IeltsStudyServiceImpl implements IeltsStudyService {

    private final IeltsStudyRecordMapper recordMapper;
    private final IeltsReviewLogMapper reviewLogMapper;
    private final IeltsDailyPlanMapper dailyPlanMapper;
    private final IeltsStudyConfig studyConfig;

    private final IeltsWordMapper wordMapper;
    private final IeltsPhraseMapper phraseMapper;
    private final IeltsParaphraseGroupMapper paraphraseGroupMapper;
    private final IeltsPronunciationPointMapper pronunciationPointMapper;
    private final IeltsGrammarPointMapper grammarPointMapper;
    private final IeltsGrammarExerciseMapper grammarExerciseMapper;
    private final IeltsSpeakingTopicMapper speakingTopicMapper;
    private final IeltsListeningItemMapper listeningItemMapper;
    private final IeltsReadingItemMapper readingItemMapper;
    private final IeltsWritingTaskMapper writingTaskMapper;

    @Override
    @Transactional
    public TodayPlanResponse getTodayPlan() {
        LocalDate today = LocalDate.now();

        // 1. 获取所有到期复习项（单条 JOIN 查询，带摘要）
        List<StudyPlanItem> reviewItems = recordMapper.findDueItemsWithSummary(today);

        // 2. 统计各类型到期复习项数量，用于计算新学配额
        Map<String, Long> dueCounts = reviewItems.stream()
                .collect(Collectors.groupingBy(StudyPlanItem::getContentType, Collectors.counting()));

        // 3. 补充新学内容（各类型按每日配额 - 已到期数量 补足）
        List<StudyPlanItem> newItems = new ArrayList<>();
        int words       = studyConfig.getDailyWords();
        int phrases     = studyConfig.getDailyPhrases();
        int grammar     = studyConfig.getDailyGrammar();
        int others      = studyConfig.getDailyOthers();

        newItems.addAll(fetchNewItems("WORD",             words,   dueCounts,
                wordMapper::findNewContent,          w -> w.getId(), w -> w.getWord()));
        newItems.addAll(fetchNewItems("PHRASE",           phrases, dueCounts,
                phraseMapper::findNewContent,        p -> p.getId(), p -> p.getPhrase()));
        newItems.addAll(fetchNewItems("PARAPHRASE",       others,  dueCounts,
                paraphraseGroupMapper::findNewContent, pg -> pg.getId(), pg -> pg.getGroupName()));
        newItems.addAll(fetchNewItems("PRONUNCIATION",    others,  dueCounts,
                pronunciationPointMapper::findNewContent, pp -> pp.getId(), pp -> pp.getTitle()));
        newItems.addAll(fetchNewItems("GRAMMAR_POINT",    grammar, dueCounts,
                grammarPointMapper::findNewContent,  gp -> gp.getId(), gp -> gp.getTitle()));
        newItems.addAll(fetchNewItems("GRAMMAR_EXERCISE", grammar, dueCounts,
                grammarExerciseMapper::findNewContent, ge -> ge.getId(),
                ge -> ge.getQuestion() != null && ge.getQuestion().length() > 80
                        ? ge.getQuestion().substring(0, 80) : ge.getQuestion()));
        newItems.addAll(fetchNewItems("SPEAKING",         others,  dueCounts,
                speakingTopicMapper::findNewContent, st -> st.getId(), st -> st.getTitle()));
        newItems.addAll(fetchNewItems("LISTENING",        others,  dueCounts,
                listeningItemMapper::findNewContent, li -> li.getId(), li -> li.getTitle()));
        newItems.addAll(fetchNewItems("READING",          others,  dueCounts,
                readingItemMapper::findNewContent,   ri -> ri.getId(), ri -> ri.getTitle()));
        newItems.addAll(fetchNewItems("WRITING",          others,  dueCounts,
                writingTaskMapper::findNewContent,   wt -> wt.getId(), wt -> wt.getTitle()));

        // 4. 合并：复习项优先，新学项置后
        List<StudyPlanItem> allItems = new ArrayList<>(reviewItems);
        allItems.addAll(newItems);

        // 5. 创建或更新今日计划记录
        int total = allItems.size();
        boolean[] isNew = {false};
        IeltsDailyPlan plan = dailyPlanMapper.findByPlanDate(today).orElseGet(() -> {
            isNew[0] = true;
            IeltsDailyPlan newPlan = new IeltsDailyPlan();
            newPlan.setId(UUID.randomUUID());
            newPlan.setPlanDate(today);
            newPlan.setCompletedItems(0);
            newPlan.setGeneratedAt(Instant.now());
            return newPlan;
        });
        plan.setTotalItems(total);
        if (isNew[0]) {
            dailyPlanMapper.insert(plan);
        } else {
            dailyPlanMapper.update(plan);
        }

        return new TodayPlanResponse(today, plan.getTotalItems(), plan.getCompletedItems(), allItems);
    }

    /**
     * 从指定内容 Mapper 中取最多 (dailyQuota - 已到期数) 条新内容，转为 StudyPlanItem。
     */
    private <T> List<StudyPlanItem> fetchNewItems(
            String contentType, int dailyQuota,
            Map<String, Long> dueCounts,
            Function<Integer, List<T>> fetcher,
            Function<T, UUID> idExtractor,
            Function<T, String> summaryExtractor) {
        int due  = dueCounts.getOrDefault(contentType, 0L).intValue();
        int need = Math.max(0, dailyQuota - due);
        if (need == 0) return List.of();
        return fetcher.apply(need).stream()
                .map(item -> StudyPlanItem.forNew(
                        contentType, idExtractor.apply(item), summaryExtractor.apply(item)))
                .toList();
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

    @Override
    @Transactional(readOnly = true)
    public List<StudyPlanItem> getRecordsByStatus(String status) {
        return recordMapper.findByStatusWithSummary(status);
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
