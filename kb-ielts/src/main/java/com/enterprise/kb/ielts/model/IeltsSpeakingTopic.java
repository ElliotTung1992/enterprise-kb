package com.enterprise.kb.ielts.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class IeltsSpeakingTopic {

    private UUID id;
    /** 话题标题 */
    private String title;
    /** 口语考试 Part（1/2/3） */
    private Integer part;
    /** 具体问题（Part 2 为完整 Cue Card） */
    private String question;
    /** 参考答案思路或范例 */
    private String referenceAnswer;
    /** 话题高分词汇，换行分隔 */
    private String keyVocabulary;
    /** 话题常用句式，换行分隔 */
    private String usefulPhrases;
    /** 难度（1基础 / 2中级 / 3高级） */
    private Integer difficulty;
    /** 话题标签，逗号分隔 */
    private String topicTags;
    private Instant createdAt;
    private Instant updatedAt;
    /** 非列字段，由 LEFT JOIN ielts_study_records 填充；null=待学习 / LEARNING=学习中 / REVIEWING=复习中 / MASTERED=已掌握 */
    private String studyStatus;
}
