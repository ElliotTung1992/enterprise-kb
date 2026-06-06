package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.Seniority;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.dto.SeniorityInference;
import com.enterprise.kb.search.service.SeniorityInferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Optional;

/**
 * 资历推断服务实现。
 *
 * <p>一次轻量 LLM 调用完成分类，要求输出单行管道格式 {@code SENIORITY|CONFIDENCE|REASON}，
 * 与本项目既有 LLM 结构化输出风格（见 {@code DomainRouterServiceImpl}）一致。任何解析失败或调用异常返回空，
 * 由调用方按去抖策略处理。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeniorityInferenceServiceImpl implements SeniorityInferenceService {

    /** 推断使用的模型提供商；资历分类是轻量任务，用便宜快模型即可。 */
    @Value("${enterprise.kb.profile.inference.provider:LLAMA_CPP}")
    private String provider;

    private final ModelProviderResolver modelProviderResolver;

    @Override
    public Optional<SeniorityInference> infer(List<String> recentQuestions) {
        if (CollectionUtils.isEmpty(recentQuestions)) {
            return Optional.empty();
        }
        try {
            ChatClient chatClient = modelProviderResolver.resolveChatClient(provider);
            String raw = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildUserPrompt(recentQuestions))
                    .call()
                    .content();
            return parse(raw);
        } catch (Exception e) {
            log.warn("资历推断调用失败：{}", e.getMessage());
            return Optional.empty();
        }
    }

    // ---- prompt 构建 ----

    private String buildUserPrompt(List<String> questions) {
        StringBuilder sb = new StringBuilder("【用户历史提问（最新在前）】\n");
        int idx = 1;
        for (String q : questions) {
            if (q == null || q.isBlank()) {
                continue;
            }
            sb.append(idx++).append(". ").append(truncate(q.strip())).append('\n');
        }
        sb.append("\n请按规定格式输出一行资历判定。");
        return sb.toString();
    }

    private String truncate(String text) {
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }

    // ---- 响应解析 ----

    /** 解析 {@code SENIORITY|CONFIDENCE|REASON}；扫描所有行取第一行能解析出合法资历的行。 */
    private Optional<SeniorityInference> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        for (String line : raw.strip().split("\\R")) {
            String trimmed = line.strip();
            if (!trimmed.contains("|")) {
                continue;
            }
            String[] parts = trimmed.split("\\|", 3);
            Seniority seniority = parseSeniority(parts[0]);
            if (seniority == null) {
                continue;
            }
            double confidence = parts.length > 1 ? parseConfidence(parts[1]) : 0.0;
            String reason = parts.length > 2 ? parts[2].strip() : "";
            return Optional.of(new SeniorityInference(seniority, confidence, reason));
        }
        log.warn("资历推断输出无法解析：{}", truncate(raw));
        return Optional.empty();
    }

    private Seniority parseSeniority(String token) {
        if (token == null) {
            return null;
        }
        try {
            return Seniority.valueOf(token.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 解析置信度并夹取到 [0,1]；不可解析按 0 处理（不会达阈值，相当于不应用）。 */
    private double parseConfidence(String token) {
        try {
            double v = Double.parseDouble(token.strip());
            return Math.max(0.0, Math.min(1.0, v));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static final String SYSTEM_PROMPT = """
            你是用户资历分类器。根据用户向知识库提出的历史问题，判断该用户的资历水平。
            你只输出资历判定，绝不回答问题本身。

            【资历等级】
            - JUNIOR：问题偏基础/概念性，常问"是什么/怎么用"，术语少。
            - INTERMEDIATE：能正确使用术语，问"如何实现/为什么如此"，有一定背景。
            - SENIOR：问题涉及权衡/边界/性能/架构/源码细节，术语精准。

            【判定规则】
            1. 综合多条问题整体判断，不被单条带偏。
            2. 证据不足或问题过少时，降低置信度。

            【输出格式】
            只输出一行，不要任何解释，三段以竖线分隔：
            SENIORITY|CONFIDENCE|REASON
            - SENIORITY：JUNIOR / INTERMEDIATE / SENIOR 之一
            - CONFIDENCE：0~1 之间的小数，表示判定置信度
            - REASON：一句中文理由

            【示例】
            INTERMEDIATE|0.78|能正确使用术语并询问实现细节
            JUNIOR|0.66|多为基础概念性提问，术语较少
            SENIOR|0.82|关注性能权衡与源码级细节
            """;
}
