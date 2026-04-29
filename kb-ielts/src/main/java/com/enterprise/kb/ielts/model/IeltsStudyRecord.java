package com.enterprise.kb.ielts.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class IeltsStudyRecord {

    private UUID id;
    /** 内容类型：WORD / PHRASE / PARAPHRASE / PRONUNCIATION / GRAMMAR_POINT / GRAMMAR_EXERCISE / SPEAKING / LISTENING / READING / WRITING */
    private String contentType;
    /** 对应内容 ID */
    private UUID contentId;
    /** 学习状态：LEARNING / REVIEWING / MASTERED */
    private String status;
    /** 难易系数（SM-2，初始 2.50） */
    private java.math.BigDecimal easeFactor;
    /** 当前复习间隔天数 */
    private Integer intervalDays;
    /** 累计复习次数 */
    private Integer repetitionCount;
    /** 下次复习日期 */
    private LocalDate nextReviewAt;
    /** 最后复习时间 */
    private Instant lastReviewedAt;
    /** 首次加入学习时间 */
    private Instant createdAt;
}
