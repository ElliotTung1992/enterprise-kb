package com.enterprise.kb.search.dto;

/**
 * 攻击守卫检查结果。
 *
 * @param blocked 是否拦截该消息
 * @param reason  拦截原因（命中的攻击类型），未拦截时为 {@code null}
 */
public record GuardResult(boolean blocked, String reason) {

    private static final GuardResult PASS = new GuardResult(false, null);

    /** 检查通过，放行。 */
    public static GuardResult pass() {
        return PASS;
    }

    /** 命中攻击特征，拦截。 */
    public static GuardResult block(String reason) {
        return new GuardResult(true, reason);
    }
}
