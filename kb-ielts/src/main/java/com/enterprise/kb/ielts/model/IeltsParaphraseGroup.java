package com.enterprise.kb.ielts.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
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
    /** 原句（含核心表达） */
    private String exampleOriginal;
    /** 改写后的句子 */
    private String exampleParaphrased;
    /** 难度（1基础 / 2中级 / 3高级） */
    private Integer difficulty;
    /** 适用技能，逗号分隔 */
    private String skillTags;
    /** 话题标签，逗号分隔 */
    private String topicTags;
    private Instant createdAt;
    private Instant updatedAt;
}
