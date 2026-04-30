package com.enterprise.kb.ielts.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class IeltsGrammarPoint {

    private UUID id;
    /** 语法要点标题 */
    private String title;
    /** 语法分类：tense / conditional / passive / relative-clause / modal / comparison / article / sentence-structure */
    private String category;
    /** 中文讲解 */
    private String explanationZh;
    /** 英文讲解 */
    private String explanationEn;
    /** 核心规则提炼，换行分隔 */
    private String keyRules;
    /** 中国学习者常见错误及纠正 */
    private String commonErrors;
    /** 难度（1基础 / 2中级 / 3高级） */
    private Integer difficulty;
    /** 适用技能（writing,speaking） */
    private String skillTags;
    /** 话题标签 */
    private String topicTags;
    private Instant createdAt;
    private Instant updatedAt;
    /** 非列字段，由 LEFT JOIN ielts_study_records 填充；null=待学习 / LEARNING=学习中 / REVIEWING=复习中 / MASTERED=已掌握 */
    private String studyStatus;
    /** 非列字段，由 ielts_examples 表关联加载 */
    private List<IeltsExample> examples = new ArrayList<>();
}
