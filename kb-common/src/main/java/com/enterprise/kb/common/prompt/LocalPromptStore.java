package com.enterprise.kb.common.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 从 classpath fallback 文件读取 prompt 模板。
 */
public class LocalPromptStore {

    private static final String VALID_PROMPT_NAME = "[A-Za-z0-9_./-]+";

    /**
     * 读取本地 fallback prompt。
     *
     * @param name prompt 名称
     * @return 缓存模板
     */
    public CachedPrompt fetch(String name) {
        validateName(name);
        String path = "prompts/" + name + ".txt";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new PromptRenderException("本地 fallback prompt 不存在: " + path);
        }
        try (var input = resource.getInputStream()) {
            return new CachedPrompt(name, 0, StreamUtils.copyToString(input, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new PromptRenderException("读取本地 fallback prompt 失败: " + path, e);
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.contains("..") || !name.matches(VALID_PROMPT_NAME)) {
            throw new PromptRenderException("非法 prompt 名称: " + name);
        }
    }
}
