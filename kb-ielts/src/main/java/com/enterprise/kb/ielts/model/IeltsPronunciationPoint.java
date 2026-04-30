package com.enterprise.kb.ielts.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class IeltsPronunciationPoint {

    private UUID id;
    /** 要点标题 */
    private String title;
    /** 分类：stress / linking / weak-form / intonation / elision / assimilation */
    private String category;
    /** 中文讲解 */
    private String explanationZh;
    /** 规则要点，换行分隔 */
    private String ruleSummary;
    /** 中国学习者常见错误及纠正 */
    private String commonMistakes;
    /** 难度（1基础 / 2中级 / 3高级） */
    private Integer difficulty;
    /** 适用技能（listening,speaking） */
    private String skillTags;
    private Instant createdAt;
    private Instant updatedAt;
    /** 非列字段，由 LEFT JOIN ielts_study_records 填充；null=待学习 / LEARNING=学习中 / REVIEWING=复习中 / MASTERED=已掌握 */
    private String studyStatus;
    /** 非列字段，由 ielts_examples 表关联加载 */
    private List<IeltsExample> examples = new ArrayList<>();
}
