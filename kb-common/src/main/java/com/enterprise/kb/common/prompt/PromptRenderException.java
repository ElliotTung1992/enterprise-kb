package com.enterprise.kb.common.prompt;

/**
 * Prompt 渲染异常。
 */
public class PromptRenderException extends RuntimeException {

    /**
     * 创建异常。
     *
     * @param message 异常消息
     */
    public PromptRenderException(String message) {
        super(message);
    }

    /**
     * 创建异常。
     *
     * @param message 异常消息
     * @param cause   原因
     */
    public PromptRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
