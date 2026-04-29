package com.enterprise.kb.ielts.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class IeltsDailyPlan {

    private UUID id;
    /** 计划日期（每天唯一） */
    private LocalDate planDate;
    /** 计划学习总数 */
    private Integer totalItems;
    /** 已完成数 */
    private Integer completedItems;
    /** 计划生成时间 */
    private Instant generatedAt;
}
