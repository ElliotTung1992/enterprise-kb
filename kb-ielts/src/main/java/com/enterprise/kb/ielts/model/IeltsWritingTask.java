package com.enterprise.kb.ielts.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class IeltsWritingTask {

    private UUID id;
    /** 题目简短描述 */
    private String title;
    /** Task 编号（1 或 2） */
    private Integer taskNumber;
    /** 考试类型：ACADEMIC / GENERAL */
    private String trainingType;
    /** Task 类型（Task1: bar-chart/line-graph/pie-chart/table/diagram/map/letter；Task2: argument/discussion/problem-solution/two-part） */
    private String taskType;
    /** 完整题目要求 */
    private String prompt;
    /** Task 1 图表数据文字描述 */
    private String imageDescription;
    /** 范文 */
    private String modelAnswer;
    /** 高分答案评分要点 */
    private String bandScoreNote;
    /** 该题型常用句式，换行分隔 */
    private String keyPhrases;
    /** 难度（1基础 / 2中级 / 3高级） */
    private Integer difficulty;
    /** 话题标签，逗号分隔 */
    private String topicTags;
    private Instant createdAt;
    private Instant updatedAt;
    /** 非列字段，由 LEFT JOIN ielts_study_records 填充；null=待学习 / LEARNING=学习中 / REVIEWING=复习中 / MASTERED=已掌握 */
    private String studyStatus;
}
