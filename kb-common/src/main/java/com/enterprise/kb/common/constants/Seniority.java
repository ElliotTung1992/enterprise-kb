package com.enterprise.kb.common.constants;

/**
 * 用户资历/角色，用于调节问答答案的深浅。
 *
 * <p>用户画像维度之一（ADR-016）。可由用户显式声明，或由离线推断写入。</p>
 */
public enum Seniority {
    /** 初级：需要从基础概念讲起。 */
    JUNIOR,
    /** 中级：可假定具备基础概念，无需从零解释。 */
    INTERMEDIATE,
    /** 高级：可直接深入细节与权衡。 */
    SENIOR
}
