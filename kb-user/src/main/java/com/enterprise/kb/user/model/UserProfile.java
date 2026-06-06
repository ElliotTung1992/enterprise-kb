package com.enterprise.kb.user.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 用户画像实体（对应 user_profiles 表）。
 *
 * <p>画像内容存于 JSONB 列，模型层以原始 JSON 字符串持有（沿用本项目 JSONB 列建模惯例，
 * 见 {@code ComplaintPlan}），结构化的 {declared, inferred, meta} 由 ProfileService 用 Jackson 解析。</p>
 */
@Getter
@Setter
public class UserProfile {

    /** 用户 ID（主键，一人一份，全局粒度） */
    private UUID userId;
    /** 画像内容，JSONB 原始字符串：{declared:{...}, inferred:{...}, meta:{...}}，见 ADR-016 */
    private String profile;
    /** 最后更新时间 */
    private Instant updatedAt = Instant.now();
}
