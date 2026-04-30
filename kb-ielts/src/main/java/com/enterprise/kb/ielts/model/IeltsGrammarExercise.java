package com.enterprise.kb.ielts.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class IeltsGrammarExercise {

    private UUID id;
    /** 关联语法要点 ID（可为 null，表示综合练习） */
    private UUID grammarPointId;
    /** 题型：fill-in-blank / error-correction / sentence-transformation / multiple-choice */
    private String questionType;
    /** 题目 */
    private String question;
    /** 选项（仅 multiple-choice，换行分隔） */
    private String options;
    /** 标准答案 */
    private String answer;
    /** 解析 */
    private String explanation;
    /** 难度（1基础 / 2中级 / 3高级） */
    private Integer difficulty;
    private Instant createdAt;
    private Instant updatedAt;
    /** 非列字段，由 LEFT JOIN ielts_study_records 填充；null=待学习 / LEARNING=学习中 / REVIEWING=复习中 / MASTERED=已掌握 */
    private String studyStatus;
}
