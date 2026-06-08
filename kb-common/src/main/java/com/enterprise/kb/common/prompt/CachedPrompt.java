package com.enterprise.kb.common.prompt;

/**
 * 未渲染的 prompt 模板缓存值。
 *
 * @param name        prompt 名称
 * @param version     LangFuse 版本；本地 fallback 为 0
 * @param rawTemplate 原始模板文本
 */
public record CachedPrompt(String name, int version, String rawTemplate) {
}
