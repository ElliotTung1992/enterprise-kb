package com.enterprise.kb.common.prompt;

/**
 * 渲染后的 prompt 结果。
 *
 * @param name    prompt 名称
 * @param version LangFuse 版本；本地 fallback 为 0
 * @param text    渲染后的最终文本
 */
public record RenderedPrompt(String name, int version, String text) {
}
