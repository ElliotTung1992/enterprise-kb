package com.enterprise.kb.ielts.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class IeltsReviewLog {

    private UUID id;
    /** 关联 ielts_study_records */
    private UUID recordId;
    /** 本次评分：AGAIN / HARD / GOOD / EASY */
    private String rating;
    /** 复习时间 */
    private Instant reviewedAt;
}
