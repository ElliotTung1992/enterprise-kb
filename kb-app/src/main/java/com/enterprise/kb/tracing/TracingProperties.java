package com.enterprise.kb.tracing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 在线 LLM Tracing 配置（ADR-015）。
 *
 * <p>对应 {@code enterprise.kb.tracing.*}。{@link #enabled} 为总开关，默认 {@code false}；
 * 其余为进 trace 正文的脱敏/截断字符上限。</p>
 */
@ConfigurationProperties(prefix = "enterprise.kb.tracing")
public class TracingProperties {

    /** tracing 总开关。关闭时 reactor 传播 hook / 脱敏 filter / 业务属性 SpanProcessor / 根 span 均不生效。 */
    private boolean enabled = false;

    /** prompt 正文进 trace 的字符上限。 */
    private int maxPromptChars = 8000;

    /** completion 正文进 trace 的字符上限。 */
    private int maxCompletionChars = 8000;

    /** tool 入参 / 返回进 trace 的字符上限。 */
    private int maxToolChars = 4000;

    /** 检索上下文进 trace 的字符上限。 */
    private int maxRetrievalChars = 4000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxPromptChars() {
        return maxPromptChars;
    }

    public void setMaxPromptChars(int maxPromptChars) {
        this.maxPromptChars = maxPromptChars;
    }

    public int getMaxCompletionChars() {
        return maxCompletionChars;
    }

    public void setMaxCompletionChars(int maxCompletionChars) {
        this.maxCompletionChars = maxCompletionChars;
    }

    public int getMaxToolChars() {
        return maxToolChars;
    }

    public void setMaxToolChars(int maxToolChars) {
        this.maxToolChars = maxToolChars;
    }

    public int getMaxRetrievalChars() {
        return maxRetrievalChars;
    }

    public void setMaxRetrievalChars(int maxRetrievalChars) {
        this.maxRetrievalChars = maxRetrievalChars;
    }
}
