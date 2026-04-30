package com.enterprise.kb.ielts.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class IeltsParaphraseGroup {

    private UUID id;
    /** 组名（核心概念，如 "increase"） */
    private String groupName;
    /** 核心表达（原词或原短语） */
    private String coreExpression;
    /** 同义替换词/短语，每行一条 */
    private String synonyms;
    /** 用法区别说明 */
    private String usageNote;
    /** 难度（1基础 / 2中级 / 3高级） */
    private Integer difficulty;
    /** 适用技能，逗号分隔 */
    private String skillTags;
    /** 话题标签，逗号分隔 */
    private String topicTags;
    private Instant createdAt;
    private Instant updatedAt;
    /** 非列字段，由 LEFT JOIN ielts_study_records 填充；null=待学习 / LEARNING=学习中 / REVIEWING=复习中 / MASTERED=已掌握 */
    private String studyStatus;
    /** 非列字段，由 ielts_examples 表关联加载（sentence=原句, translation=改写句） */
    private List<IeltsExample> examples = new ArrayList<>();
}
