package com.enterprise.kb.search.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 一次离线评估运行记录。
 */
@Getter
@Setter
public class EvalRun {

    private UUID id;
    /** 数据集名称 */
    private String dataset;
    /** 运行状态：RUNNING / SUCCEEDED / FAILED */
    private String status;
    /** 运行配置 JSON */
    private String configJson;
    /** 汇总指标 JSON */
    private String summaryJson;
    /** 开始时间 */
    private Instant startedAt;
    /** 完成时间 */
    private Instant completedAt;
    /** 创建时间 */
    private Instant createdAt;
}
