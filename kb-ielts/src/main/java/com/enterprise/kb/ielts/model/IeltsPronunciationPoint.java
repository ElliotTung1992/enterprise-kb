package com.enterprise.kb.ielts.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
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
    /** 例词/例句，含音标，换行分隔 */
    private String examples;
    /** 中国学习者常见错误及纠正 */
    private String commonMistakes;
    /** 难度（1基础 / 2中级 / 3高级） */
    private Integer difficulty;
    /** 适用技能（listening,speaking） */
    private String skillTags;
    private Instant createdAt;
    private Instant updatedAt;
}
