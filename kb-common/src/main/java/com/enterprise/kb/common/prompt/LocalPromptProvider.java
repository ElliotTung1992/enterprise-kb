package com.enterprise.kb.common.prompt;

import java.util.Map;

/**
 * 只读取本地 fallback 的 PromptProvider。
 */
public class LocalPromptProvider implements PromptProvider {

    private final LocalPromptStore localPromptStore;
    private final MustacheLite mustacheLite;

    public LocalPromptProvider(LocalPromptStore localPromptStore, MustacheLite mustacheLite) {
        this.localPromptStore = localPromptStore;
        this.mustacheLite = mustacheLite;
    }

    @Override
    public String render(String name, Map<String, Object> vars) {
        return get(name, vars).text();
    }

    @Override
    public RenderedPrompt get(String name, Map<String, Object> vars) {
        CachedPrompt prompt = localPromptStore.fetch(name);
        return new RenderedPrompt(prompt.name(), prompt.version(), mustacheLite.render(prompt.rawTemplate(), vars));
    }
}
