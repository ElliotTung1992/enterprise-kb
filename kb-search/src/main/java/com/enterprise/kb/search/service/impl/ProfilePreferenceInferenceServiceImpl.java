package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.AnswerLanguage;
import com.enterprise.kb.common.constants.AnswerLength;
import com.enterprise.kb.common.constants.AnswerStyle;
import com.enterprise.kb.common.constants.Seniority;
import com.enterprise.kb.common.prompt.PromptProvider;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.service.ProfilePreferenceInferenceService;
import com.enterprise.kb.user.dto.InferredSignals;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;

/**
 * 用户画像统一推断实现。
 *
 * <p>一次 LLM 调用要求输出四行 {@code FIELD|VALUE|CONFIDENCE}（FIELD ∈ SENIORITY/LENGTH/LANGUAGE/STYLE），
 * 风格同 {@code DomainRouterServiceImpl}。明说的偏好按高置信、行为推断按证据给置信；解析失败/异常返回
 * {@link InferredSignals#empty()}（不更新任何维）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfilePreferenceInferenceServiceImpl implements ProfilePreferenceInferenceService {

    private static final String PROFILE_PROMPT = "kb/profile/preference-inference";

    /** 推断使用的模型提供商（轻量任务，用便宜快模型）。 */
    @Value("${enterprise.kb.profile.inference.provider:LLAMA_CPP}")
    private String provider;

    private final ModelProviderResolver modelProviderResolver;
    private final PromptProvider promptProvider;

    @Override
    public InferredSignals infer(List<String> recentMessages) {
        if (CollectionUtils.isEmpty(recentMessages)) {
            return InferredSignals.empty();
        }
        try {
            ChatClient chatClient = modelProviderResolver.resolveChatClient(provider);
            String raw = chatClient.prompt()
                    .system(promptProvider.render(PROFILE_PROMPT, Map.of()))
                    .user(buildUserPrompt(recentMessages))
                    .call()
                    .content();
            return parse(raw);
        } catch (Exception e) {
            log.warn("画像统一推断调用失败：{}", e.getMessage());
            return InferredSignals.empty();
        }
    }

    // ---- prompt 构建 ----

    private String buildUserPrompt(List<String> messages) {
        StringBuilder sb = new StringBuilder("【用户近期消息（最新在前）】\n");
        int idx = 1;
        for (String m : messages) {
            if (m == null || m.isBlank()) {
                continue;
            }
            sb.append(idx++).append(". ").append(truncate(m.strip())).append('\n');
        }
        sb.append("\n请按规定格式输出四行画像判定。");
        return sb.toString();
    }

    private String truncate(String text) {
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }

    // ---- 响应解析：四行 FIELD|VALUE|CONFIDENCE，逐行累积 ----

    private InferredSignals parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return InferredSignals.empty();
        }
        Seniority sen = null;
        Double senC = null;
        AnswerLength len = null;
        Double lenC = null;
        AnswerLanguage lang = null;
        Double langC = null;
        AnswerStyle sty = null;
        Double styC = null;
        for (String line : raw.strip().split("\\R")) {
            String trimmed = line.strip();
            if (!trimmed.contains("|")) {
                continue;
            }
            String[] p = trimmed.split("\\|", 3);
            String field = p[0].strip().toUpperCase();
            String value = p.length > 1 ? p[1].strip().toUpperCase() : "";
            double conf = p.length > 2 ? parseConfidence(p[2]) : 0.0;
            if (value.isEmpty() || "NONE".equals(value)) {
                continue;
            }
            switch (field) {
                case "SENIORITY" -> {
                    Seniority v = enumOrNull(Seniority.class, value);
                    if (v != null) {
                        sen = v;
                        senC = conf;
                    }
                }
                case "LENGTH" -> {
                    AnswerLength v = enumOrNull(AnswerLength.class, value);
                    if (v != null) {
                        len = v;
                        lenC = conf;
                    }
                }
                case "LANGUAGE" -> {
                    AnswerLanguage v = enumOrNull(AnswerLanguage.class, value);
                    if (v != null) {
                        lang = v;
                        langC = conf;
                    }
                }
                case "STYLE" -> {
                    AnswerStyle v = enumOrNull(AnswerStyle.class, value);
                    if (v != null) {
                        sty = v;
                        styC = conf;
                    }
                }
                default -> {
                    // 忽略未知字段
                }
            }
        }
        return new InferredSignals(sen, senC, len, lenC, lang, langC, sty, styC);
    }

    private <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 解析置信度并夹取到 [0,1]；不可解析按 0（不会达阈值，相当于不应用）。 */
    private double parseConfidence(String token) {
        try {
            double v = Double.parseDouble(token.strip());
            return Math.max(0.0, Math.min(1.0, v));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

}
