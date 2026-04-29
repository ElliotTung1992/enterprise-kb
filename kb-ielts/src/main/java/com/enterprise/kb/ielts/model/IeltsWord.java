package com.enterprise.kb.ielts.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class IeltsWord {

    private UUID id;
    /** 单词原形 */
    private String word;
    /** 英式音标 */
    private String phoneticUk;
    /** 美式音标 */
    private String phoneticUs;
    /** 词性（n./v./adj. 等） */
    private String partOfSpeech;
    /** 中文释义，多义词换行分隔 */
    private String definitionZh;
    /** 英文释义 */
    private String definitionEn;
    /** 例句（英文） */
    private String exampleSentence;
    /** 例句翻译 */
    private String exampleTranslation;
    /** 雅思出现频率（1-5，5最高） */
    private Integer frequencyLevel;
    /** 词表来源：AWL / GSL / IELTS */
    private String wordList;
    /** 难度（1基础 / 2中级 / 3高级） */
    private Integer difficulty;
    /** 适用技能，逗号分隔 */
    private String skillTags;
    /** 话题标签，逗号分隔 */
    private String topicTags;
    private Instant createdAt;
    private Instant updatedAt;
}
