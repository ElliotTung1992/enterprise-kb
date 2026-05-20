package com.enterprise.kb.search.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 单个评估用例在一次运行中的结果。
 */
@Getter
@Setter
public class EvalRunResult {

    private UUID id;
    /** 评估运行 ID */
    private UUID evalRunId;
    /** 评估用例 ID */
    private UUID evalCaseId;
    /** 结果状态：PASSED / FAILED / SKIPPED */
    private String status;
    /** 实际输出 JSON */
    private String actualJson;
    /** 断言结果 JSON */
    private String assertionResultJson;
    /** 失败原因 */
    private String failureReason;
    /** 创建时间 */
    private Instant createdAt;
}
