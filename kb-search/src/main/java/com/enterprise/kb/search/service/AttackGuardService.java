package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.GuardResult;

/**
 * 攻击守卫服务。
 *
 * <p>位于客服助手流水线最前端，先于 Tier-1 域路由器执行。负责拦截提示词注入、
 * 角色扮演越狱等攻击性输入——这类输入不属于业务意图，与域分类是两件事，
 * 故独立成守卫、不进路由器，避免互相干扰。
 */
public interface AttackGuardService {

    /**
     * 检查用户消息是否包含攻击特征。
     *
     * @param message 用户消息正文
     * @return 检查结果；命中攻击特征时 {@link GuardResult#blocked()} 为 {@code true}
     */
    GuardResult inspect(String message);
}
