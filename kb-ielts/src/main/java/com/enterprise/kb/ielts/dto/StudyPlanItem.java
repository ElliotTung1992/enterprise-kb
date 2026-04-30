package com.enterprise.kb.ielts.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * 今日学习计划中的单个条目，统一表示复习项和新学项。
 * MyBatis 通过 Setter 注入 DB 结果；Java 代码通过静态工厂方法构造新学项。
 */
@Getter
@Setter
public class StudyPlanItem {

    /** 内容类型：WORD / PHRASE / PARAPHRASE / PRONUNCIATION / GRAMMAR_POINT /
     *  GRAMMAR_EXERCISE / SPEAKING / LISTENING / READING / WRITING */
    private String contentType;

    /** 对应内容表的主键 */
    private UUID contentId;

    /** 展示摘要：单词原形 / 短语 / 话题标题 / 题干前80字等，供前端翻卡正面显示 */
    private String summary;

    /**
     * 学习模式：NEW=首次学习（study_records 中尚无记录），REVIEW=间隔复习
     * MyBatis 映射到期复习项时此字段留 null，服务层统一设为 "REVIEW"
     */
    private String studyMode;

    /** 复习项的 study_records.id；studyMode=NEW 时为 null */
    private UUID recordId;

    /** 下次复习日期，来自 study_records.next_review_at */
    private java.time.LocalDate nextReviewAt;

    public static StudyPlanItem forReview(UUID recordId, String contentType, UUID contentId, String summary) {
        StudyPlanItem item = new StudyPlanItem();
        item.recordId = recordId;
        item.contentType = contentType;
        item.contentId = contentId;
        item.summary = summary;
        item.studyMode = "REVIEW";
        return item;
    }

    public static StudyPlanItem forNew(String contentType, UUID contentId, String summary) {
        StudyPlanItem item = new StudyPlanItem();
        item.contentType = contentType;
        item.contentId = contentId;
        item.summary = summary;
        item.studyMode = "NEW";
        return item;
    }
}
