package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.dto.GuardResult;
import com.enterprise.kb.search.service.AttackGuardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 攻击守卫服务实现（MVP：纯规则）。
 *
 * <p>用关键词/正则匹配高置信度的提示词注入与角色扮演越狱特征。规则只覆盖确定性高的
 * 表达，宁可漏判（交由路由器与子 Agent 的 system prompt 安全规则兜底）也不误伤正常业务咨询。
 * 后续可在此基础上叠加小模型分类，但不改变"守卫前置、不进路由器"的边界。
 */
@Slf4j
@Service
public class AttackGuardServiceImpl implements AttackGuardService {

    /** 提示词注入：要求忽略/覆盖既有指令。 */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("忽略(掉)?(之前|以上|上述|前面|所有|你的).{0,8}(指令|提示|规则|设定|prompt|命令)"),
            Pattern.compile("(重复|输出|告诉我|打印|泄露).{0,8}(你的)?(系统)?(指令|提示词|system\\s*prompt|设定)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("ignore\\s+(the\\s+)?(previous|above|prior|all|your)\\s+(instruction|prompt|rule)",
                    Pattern.CASE_INSENSITIVE)
    );

    /** 角色扮演越狱：要求脱离客服身份扮演其他角色。 */
    private static final List<Pattern> ROLEPLAY_PATTERNS = List.of(
            Pattern.compile("(扮演|假装你是|你现在是|从现在起你是|你不再是).{0,12}(不受限|没有限制|开发者模式|另一个|其他)"),
            Pattern.compile("(进入|启用|开启).{0,6}(开发者|developer|DAN|越狱|jailbreak)\\s*模式",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("(pretend|act as|you are now|from now on you are)\\b.{0,20}(unrestricted|no rule|developer mode)",
                    Pattern.CASE_INSENSITIVE)
    );

    @Override
    public GuardResult inspect(String message) {
        if (message == null || message.isBlank()) {
            return GuardResult.pass();
        }
        if (matchesAny(message, INJECTION_PATTERNS)) {
            log.warn("攻击守卫拦截：疑似提示词注入，message={}", truncate(message));
            return GuardResult.block("PROMPT_INJECTION");
        }
        if (matchesAny(message, ROLEPLAY_PATTERNS)) {
            log.warn("攻击守卫拦截：疑似角色扮演越狱，message={}", truncate(message));
            return GuardResult.block("ROLEPLAY_JAILBREAK");
        }
        return GuardResult.pass();
    }

    private boolean matchesAny(String message, List<Pattern> patterns) {
        return patterns.stream().anyMatch(p -> p.matcher(message).find());
    }

    private String truncate(String message) {
        return message.length() > 80 ? message.substring(0, 80) : message;
    }
}
