package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.AnswerLanguage;
import com.enterprise.kb.common.constants.AnswerLength;
import com.enterprise.kb.common.constants.AnswerStyle;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.service.AnswerPreferenceCaptureService;
import com.enterprise.kb.user.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 对话内答案偏好捕获实现（ADR-016 决策 15）。
 *
 * <p>每轮先用正则**预过滤**持久线索（不命中直接跳过，正常提问零 LLM 成本）；命中才调一次轻量 LLM，
 * 要求输出单行 {@code FIELD|VALUE|DURABLE}（风格同 {@code DomainRouterServiceImpl}）。判为持久且字段合法
 * 才经 {@link ProfileService#mergeDeclaredPreference} 写入显式层并回执。best-effort，异常一律吞咽不影响问答。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerPreferenceCaptureServiceImpl implements AnswerPreferenceCaptureService {

    /** 捕获总开关。 */
    @Value("${enterprise.kb.profile.capture.enabled:true}")
    private boolean enabled;
    /** 捕获判定用的模型提供商（轻量任务）。 */
    @Value("${enterprise.kb.profile.capture.provider:LLAMA_CPP}")
    private String provider;

    private final ModelProviderResolver modelProviderResolver;
    private final ProfileService profileService;

    /** 持久线索预过滤：不含这些词的消息直接跳过，避免每轮 LLM 成本（漏判只是退化为不捕获，安全）。 */
    private static final Pattern DURABLE_CUE = Pattern.compile(
            "以后|今后|从今|从现在起|默认|总是|一直|每次|每回|别再|不要再|都用|记住");

    @Override
    public Optional<String> capture(UUID userId, String message) {
        if (!enabled || userId == null || message == null || message.isBlank()) {
            return Optional.empty();
        }
        if (!DURABLE_CUE.matcher(message).find()) {
            return Optional.empty();
        }
        try {
            ChatClient chatClient = modelProviderResolver.resolveChatClient(provider);
            String raw = chatClient.prompt().system(SYSTEM_PROMPT).user(message).call().content();
            return apply(userId, raw);
        } catch (Exception e) {
            log.warn("对话偏好捕获失败：userId={}，{}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    /** 解析 {@code FIELD|VALUE|DURABLE}；扫描所有行，取第一行可解析者。仅持久（DURABLE=YES）才写入。 */
    private Optional<String> apply(UUID userId, String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        for (String line : raw.strip().split("\\R")) {
            String trimmed = line.strip();
            if (!trimmed.contains("|")) {
                continue;
            }
            String[] parts = trimmed.split("\\|", 3);
            String field = parts[0].strip().toUpperCase();
            String value = parts.length > 1 ? parts[1].strip().toUpperCase() : "";
            boolean durable = parts.length > 2 && "YES".equalsIgnoreCase(parts[2].strip());
            if ("NONE".equals(field) || !durable) {
                return Optional.empty();
            }
            return persist(userId, field, value);
        }
        return Optional.empty();
    }

    private Optional<String> persist(UUID userId, String field, String value) {
        try {
            switch (field) {
                case "LANGUAGE" -> {
                    AnswerLanguage v = AnswerLanguage.valueOf(value);
                    profileService.mergeDeclaredPreference(userId, v, null, null);
                    return Optional.of(notice("以后默认用" + languageLabel(v) + "回复"));
                }
                case "LENGTH" -> {
                    AnswerLength v = AnswerLength.valueOf(value);
                    profileService.mergeDeclaredPreference(userId, null, v, null);
                    return Optional.of(notice("以后默认" + lengthLabel(v) + "回答"));
                }
                case "STYLE" -> {
                    AnswerStyle v = AnswerStyle.valueOf(value);
                    profileService.mergeDeclaredPreference(userId, null, null, v);
                    return Optional.of(notice("以后默认" + styleLabel(v)));
                }
                default -> {
                    return Optional.empty();
                }
            }
        } catch (IllegalArgumentException e) {
            log.warn("捕获到的偏好值非法，跳过：field={}，value={}", field, value);
            return Optional.empty();
        }
    }

    private String notice(String what) {
        return "（已记住：" + what + "，可在设置中修改）";
    }

    private String languageLabel(AnswerLanguage v) {
        return switch (v) {
            case ZH -> "中文";
            case EN -> "英文";
            case FOLLOW -> "提问语言";
        };
    }

    private String lengthLabel(AnswerLength v) {
        return switch (v) {
            case CONCISE -> "简洁";
            case MEDIUM -> "适中";
            case DETAILED -> "详细";
        };
    }

    private String styleLabel(AnswerStyle v) {
        return switch (v) {
            case DIRECT -> "直接给结论";
            case EXPLAINED -> "带解释";
            case WITH_EXAMPLES -> "带示例";
        };
    }

    private static final String SYSTEM_PROMPT = """
            你判断用户消息是否在表达一个【持久的】回答偏好（关于助手以后如何回复），并抽取出来。
            只认明确的持久意图（如"以后/今后/默认/总是/每次/别再…"）；一次性的（"这次/本轮/就这次"）一律判 NO。

            偏好维度只认三种：
            - LANGUAGE 回答语言 → ZH(中文) / EN(英文)
            - LENGTH 答案长度 → CONCISE(简洁) / MEDIUM(适中) / DETAILED(详细)
            - STYLE 答案风格 → DIRECT(直接给结论) / EXPLAINED(带解释) / WITH_EXAMPLES(带示例)

            只输出一行，不要任何解释：FIELD|VALUE|DURABLE
            - FIELD：LANGUAGE / LENGTH / STYLE；无可识别偏好则输出 NONE|NONE|NO
            - VALUE：上面对应的枚举值
            - DURABLE：YES（持久）/ NO（一次性或不确定）

            示例：
            以后都用中文回复我 → LANGUAGE|ZH|YES
            以后回答简洁点，别太长 → LENGTH|CONCISE|YES
            这次给我讲详细些 → LENGTH|DETAILED|NO
            以后多举例子 → STYLE|WITH_EXAMPLES|YES
            帮我查下物流到哪了 → NONE|NONE|NO
            """;
}
