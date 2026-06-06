package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.AnswerLanguage;
import com.enterprise.kb.common.constants.AnswerLength;
import com.enterprise.kb.common.constants.AnswerStyle;
import com.enterprise.kb.common.constants.Seniority;
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

    /** 推断使用的模型提供商（轻量任务，用便宜快模型）。 */
    @Value("${enterprise.kb.profile.inference.provider:LLAMA_CPP}")
    private String provider;

    private final ModelProviderResolver modelProviderResolver;

    @Override
    public InferredSignals infer(List<String> recentMessages) {
        if (CollectionUtils.isEmpty(recentMessages)) {
            return InferredSignals.empty();
        }
        try {
            ChatClient chatClient = modelProviderResolver.resolveChatClient(provider);
            String raw = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
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

    private static final String SYSTEM_PROMPT = """
            你从用户向知识库提出的近期消息，推断该用户的回答偏好画像，共四维。你只输出推断结果，绝不回答问题本身。
            用户若在消息里**明说**偏好（如"以后用中文""以后简洁点""以后多举例"），按高置信抽取；
            其余维从提问内容/术语谨慎推断，证据不足就给低置信或 NONE。

            四个维度与取值：
            - SENIORITY 资历：JUNIOR / INTERMEDIATE / SENIOR
            - LENGTH 答案长度：CONCISE / MEDIUM / DETAILED
            - LANGUAGE 回答语言：ZH / EN
            - STYLE 答案风格：DIRECT / EXPLAINED / WITH_EXAMPLES

            输出恰好四行，每行三段以竖线分隔：FIELD|VALUE|CONFIDENCE
            - FIELD：SENIORITY / LENGTH / LANGUAGE / STYLE 各一行
            - VALUE：对应取值；该维无可靠信号则填 NONE
            - CONFIDENCE：0~1 小数

            示例（用户近期说过"以后都用中文回复"，问题偏进阶）：
            SENIORITY|INTERMEDIATE|0.7
            LENGTH|NONE|0
            LANGUAGE|ZH|0.95
            STYLE|NONE|0
            """;
}
