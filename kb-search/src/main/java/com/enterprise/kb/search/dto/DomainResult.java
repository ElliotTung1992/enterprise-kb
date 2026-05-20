package com.enterprise.kb.search.dto;

import java.util.regex.Pattern;

/**
 * {@code DomainHandler} 的域内处理结果。
 *
 * @param answer       返回给用户的回复文本
 * @param awaitingSlot 本轮是否在向用户索要某个槽位（如订单号）；为 {@code true} 时
 *                     下一轮命中硬规则、跳过 Tier-1 路由，直接回到当前域处理
 */
public record DomainResult(String answer, boolean awaitingSlot) {

    /**
     * 启发式：回复是否在向用户索要订单号。命中则视为"等待槽位回填"。
     * <p>只识别"索要订单标识"这一类反问——而非所有反问——以免把"是否提交申请"
     * 这类开放反问也锁死路由，导致用户中途的意图漂移（如改为投诉）被吞掉。</p>
     * <p>字符类只把 {@code 。！？} 当作句末边界；换行 {@code \n} 不再排除，
     * 以兼容 LLM 用 markdown 列表分多行索要槽位的常见格式，例如
     * <code>请您提供以下信息：\n\n1. **订单号**：…</code>。窗口拓到 30 字符以容纳
     * 形如 <code>1. **订单号**</code> 的列表项与粗体装饰。</p>
     */
    private static final Pattern SLOT_REQUEST = Pattern.compile(
            "(订单号|订单编号)[^。！？]{0,30}(提供|发我|发送|告知|告诉|是多少|麻烦|输入)"
                    + "|(提供|发我|发送|告知|输入|麻烦)[^。！？]{0,30}(订单号|订单编号)");

    /** 普通 Agent 回复：自动按启发式判断是否在索要槽位。 */
    public static DomainResult of(String answer) {
        boolean awaiting = answer != null && SLOT_REQUEST.matcher(answer).find();
        return new DomainResult(answer, awaiting);
    }

    /** 终态回复（已提交人工审核 / 已升级投诉等），流程闭环，不等待槽位。 */
    public static DomainResult terminal(String answer) {
        return new DomainResult(answer, false);
    }
}
