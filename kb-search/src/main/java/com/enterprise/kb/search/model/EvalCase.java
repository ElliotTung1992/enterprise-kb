package com.enterprise.kb.search.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 离线评估用例。
 */
@Getter
@Setter
public class EvalCase {

    private UUID id;
    /** 用例类型：ROUTING_TOOL / RETRIEVAL / ANSWER / END_TO_END */
    private String caseType;
    /** 用例名称 */
    private String name;
    /** 数据集名称，如 smoke / frozen / regression */
    private String dataset;
    /** 回放输入 JSON */
    private String inputJson;
    /** 真值与断言 JSON */
    private String expectedJson;
    /** 副作用工具 mock 输出 JSON */
    private String mockOutputsJson;
    /** 是否启用 */
    private boolean enabled;
    /** 创建人用户 ID */
    private UUID createdBy;
    /** 创建时间 */
    private Instant createdAt;
    /** 更新时间 */
    private Instant updatedAt;
}
