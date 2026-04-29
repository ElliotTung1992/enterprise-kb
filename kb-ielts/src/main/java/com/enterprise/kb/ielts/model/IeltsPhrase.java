package com.enterprise.kb.ielts.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class IeltsPhrase {

    private UUID id;
    /** 短语原文 */
    private String phrase;
    /** 中文含义 */
    private String meaningZh;
    /** 用法说明 */
    private String usageNote;
    /** 示例句 */
    private String exampleSentence;
    /** 示例句翻译 */
    private String exampleTranslation;
    /** 类型：signal-word / sentence-frame / collocation / connector / idiom */
    private String category;
    /** 难度（1基础 / 2中级 / 3高级） */
    private Integer difficulty;
    /** 适用技能，逗号分隔 */
    private String skillTags;
    /** 话题标签，逗号分隔 */
    private String topicTags;
    private Instant createdAt;
    private Instant updatedAt;
}
