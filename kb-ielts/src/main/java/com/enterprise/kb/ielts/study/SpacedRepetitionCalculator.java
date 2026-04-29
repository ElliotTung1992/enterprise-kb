package com.enterprise.kb.ielts.study;

import com.enterprise.kb.ielts.model.IeltsStudyRecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * SM-2 间隔重复算法计算器。
 * WORD / PHRASE 使用完整 SM-2；其余内容类型使用简化的 LEARNING/MASTERED 切换。
 */
public class SpacedRepetitionCalculator {

    private static final BigDecimal MIN_EASE_FACTOR = new BigDecimal("1.30");
    private static final Set<String> SM2_TYPES = Set.of("WORD", "PHRASE");

    private SpacedRepetitionCalculator() {}

    /**
     * 根据评分更新学习记录的 SM-2 参数
     *
     * @param record 当前学习记录
     * @param rating 评分：AGAIN / HARD / GOOD / EASY
     */
    public static void apply(IeltsStudyRecord record, String rating) {
        if (SM2_TYPES.contains(record.getContentType())) {
            applySm2(record, rating);
        } else {
            applySimple(record, rating);
        }
        record.setLastReviewedAt(Instant.now());
        record.setNextReviewAt(LocalDate.now().plusDays(record.getIntervalDays()));
    }

    private static void applySm2(IeltsStudyRecord record, String rating) {
        int rep = record.getRepetitionCount();
        BigDecimal ef = record.getEaseFactor();
        int interval = record.getIntervalDays();

        switch (rating) {
            case "AGAIN" -> {
                record.setRepetitionCount(0);
                record.setIntervalDays(1);
                record.setEaseFactor(ef.subtract(new BigDecimal("0.20")).max(MIN_EASE_FACTOR));
                record.setStatus("LEARNING");
            }
            case "HARD" -> {
                int newRep = rep + 1;
                record.setRepetitionCount(newRep);
                record.setIntervalDays(Math.max(1, (int) Math.floor(interval * 1.2)));
                record.setEaseFactor(ef.subtract(new BigDecimal("0.15")).max(MIN_EASE_FACTOR));
                record.setStatus(newRep >= 2 ? "REVIEWING" : "LEARNING");
            }
            case "GOOD" -> {
                int newRep = rep + 1;
                int newInterval = computeInterval(newRep, interval, ef, 1.0);
                record.setRepetitionCount(newRep);
                record.setIntervalDays(newInterval);
                record.setStatus(newRep >= 5 && newInterval >= 21 ? "MASTERED" : "REVIEWING");
            }
            case "EASY" -> {
                int newRep = rep + 1;
                BigDecimal newEf = ef.add(new BigDecimal("0.15"));
                int newInterval = computeInterval(newRep, interval, ef, 1.3);
                record.setRepetitionCount(newRep);
                record.setIntervalDays(newInterval);
                record.setEaseFactor(newEf);
                record.setStatus(newRep >= 3 && newInterval >= 21 ? "MASTERED" : "REVIEWING");
            }
            default -> throw new IllegalArgumentException("Invalid rating: " + rating);
        }
    }

    private static int computeInterval(int rep, int prevInterval, BigDecimal ef, double bonus) {
        return switch (rep) {
            case 1 -> bonus > 1.0 ? 4 : 1;
            case 2 -> bonus > 1.0 ? 10 : 6;
            default -> Math.max(1, (int) Math.floor(prevInterval * ef.doubleValue() * bonus));
        };
    }

    private static void applySimple(IeltsStudyRecord record, String rating) {
        boolean mastered = "GOOD".equals(rating) || "EASY".equals(rating);
        record.setStatus(mastered ? "MASTERED" : "LEARNING");
        record.setRepetitionCount(record.getRepetitionCount() + 1);
        // 掌握后下次复习推迟一年，未掌握明天再试
        record.setIntervalDays(mastered ? 365 : 1);
    }
}
