package com.enterprise.kb.common.tracing;

import java.util.regex.Pattern;

/**
 * 进 trace 正文的 MVP 级敏感信息脱敏 + 长度截断工具（ADR-015 D5）。
 *
 * <p>仅做正则级脱敏（非语义级 DLP），覆盖常见高危模式：Bearer Token / JWT /
 * API Key / 密码 / 密钥字段 / email / 中国大陆手机号，统一替换为固定占位符。
 * 截断只保留前 N 字符并附原始长度标记，避免超长正文撑爆 ClickHouse。</p>
 *
 * <p>在埋点源头（tool 拦截器、检索 span、ObservationFilter 兜底）调用，<b>不影响</b>喂给
 * 模型的真实 prompt/completion。</p>
 */
public final class SensitiveDataRedactor {

    private static final String MASK = "[REDACTED]";

    /** 形如 {@code Bearer xxxxx} 的授权头。 */
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._\\-]+");
    /** 三段式 JWT。 */
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+");
    /** {@code apiKey/secret/password/token = xxx} 这类键值。 */
    private static final Pattern SECRET_KV = Pattern.compile(
            "(?i)(api[_-]?key|secret|password|passwd|token|access[_-]?key)\\s*[\"']?\\s*[:=]\\s*[\"']?[^\\s\"',}]+");
    /** email。 */
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    /** 中国大陆手机号。 */
    private static final Pattern PHONE_CN = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    private SensitiveDataRedactor() {
    }

    /**
     * 对文本做正则脱敏。
     *
     * @param text 原始文本
     * @return 脱敏后文本，{@code null} 原样返回
     */
    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = JWT.matcher(text).replaceAll(MASK);
        result = BEARER.matcher(result).replaceAll(MASK);
        result = SECRET_KV.matcher(result).replaceAll(MASK);
        result = EMAIL.matcher(result).replaceAll(MASK);
        result = PHONE_CN.matcher(result).replaceAll(MASK);
        return result;
    }

    /**
     * 截断文本到指定字符数，超出部分仅保留截断标记与原始长度。
     *
     * @param text     原始文本
     * @param maxChars 字符数上限（{@code <= 0} 表示不截断）
     * @return 截断后文本
     */
    public static String truncate(String text, int maxChars) {
        if (text == null || maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "…[truncated, originalLength=" + text.length() + "]";
    }

    /**
     * 先脱敏再截断。
     *
     * @param text     原始文本
     * @param maxChars 字符数上限
     * @return 处理后文本
     */
    public static String redactAndTruncate(String text, int maxChars) {
        return truncate(redact(text), maxChars);
    }
}
