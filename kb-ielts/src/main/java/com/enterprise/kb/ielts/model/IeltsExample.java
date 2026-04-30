package com.enterprise.kb.ielts.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class IeltsExample {

    private UUID id;
    /** 所属内容类型：WORD / PHRASE / PARAPHRASE / PRONUNCIATION / GRAMMAR_POINT */
    private String contentType;
    /** 所属内容 ID */
    private UUID contentId;
    /** 例句（英文）/ 原句 */
    private String sentence;
    /** 例句翻译（中文）/ 改写句 */
    private String translation;
    /** 补充说明 */
    private String note;
    /** 排序序号 */
    private int sortOrder;
    private Instant createdAt;
}
