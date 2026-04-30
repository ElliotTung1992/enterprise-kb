package com.enterprise.kb.ielts.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class IeltsListeningItem {

    private UUID id;
    /** 练习标题 */
    private String title;
    /** Section 编号（1-4） */
    private Integer section;
    /** 题型：form-completion / note-completion / table-completion / multiple-choice / matching / map-labelling / flow-chart */
    private String questionType;
    /** 听力场景描述 */
    private String contextDescription;
    /** 关键脚本片段 */
    private String scriptExcerpt;
    /** 题目，换行分隔 */
    private String questions;
    /** 答案及解析 */
    private String answers;
    /** 该题型作答技巧 */
    private String tips;
    /** 难度（1基础 / 2中级 / 3高级） */
    private Integer difficulty;
    /** 话题标签，逗号分隔 */
    private String topicTags;
    private Instant createdAt;
    private Instant updatedAt;
    /** 非列字段，由 LEFT JOIN ielts_study_records 填充；null=待学习 / LEARNING=学习中 / REVIEWING=复习中 / MASTERED=已掌握 */
    private String studyStatus;
}
