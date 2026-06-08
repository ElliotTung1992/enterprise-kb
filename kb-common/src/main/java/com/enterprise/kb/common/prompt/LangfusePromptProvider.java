package com.enterprise.kb.common.prompt;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.enterprise.kb.common.tracing.TracingAttributes;
import com.enterprise.kb.common.tracing.TracingContextHolder;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * LangFuse + 本地 fallback 的 PromptProvider。
 */
@Slf4j
public class LangfusePromptProvider implements PromptProvider {

    private final LoadingCache<String, CachedPrompt> cache;
    private final LocalPromptStore localPromptStore;
    private final MustacheLite mustacheLite;
    private final MeterRegistry meterRegistry;

    public LangfusePromptProvider(LangfusePromptClient langfusePromptClient,
                                  LocalPromptStore localPromptStore,
                                  MustacheLite mustacheLite,
                                  PromptProperties properties,
                                  Optional<MeterRegistry> meterRegistry) {
        this.localPromptStore = localPromptStore;
        this.mustacheLite = mustacheLite;
        this.meterRegistry = meterRegistry.orElse(null);
        this.cache = Caffeine.newBuilder()
                .refreshAfterWrite(properties.getCache().getTtl())
                .expireAfterWrite(properties.getCache().getExpire())
                .maximumSize(properties.getCache().getMaximumSize())
                .build(langfusePromptClient::fetch);
    }

    @Override
    public String render(String name, Map<String, Object> vars) {
        return get(name, vars).text();
    }

    @Override
    public RenderedPrompt get(String name, Map<String, Object> vars) {
        return get(name, vars, false);
    }

    @Override
    public String renderForTrace(String name, Map<String, Object> vars) {
        return getForTrace(name, vars).text();
    }

    @Override
    public RenderedPrompt getForTrace(String name, Map<String, Object> vars) {
        return get(name, vars, true);
    }

    private RenderedPrompt get(String name, Map<String, Object> vars, boolean recordTrace) {
        CachedPrompt prompt = fetchWithFallback(name);
        try {
            RenderedPrompt rendered = renderPrompt(prompt, vars);
            if (recordTrace) {
                recordPromptVersion(prompt);
            }
            return rendered;
        } catch (PromptRenderException e) {
            if (prompt.version() == 0) {
                throw e;
            }
            recordRenderFailure();
            log.warn("LangFuse prompt 渲染失败，使用本地 fallback：name={}, version={}",
                    prompt.name(), prompt.version(), e);
            CachedPrompt fallback = localPromptStore.fetch(name);
            RenderedPrompt rendered = renderPrompt(fallback, vars);
            if (recordTrace) {
                recordPromptVersion(fallback);
            }
            return rendered;
        }
    }

    /**
     * 预热缓存，只拉模板不渲染变量。
     *
     * @param name prompt 名称
     */
    public void preload(String name) {
        cache.get(name);
    }

    private CachedPrompt fetchWithFallback(String name) {
        try {
            return cache.get(name);
        } catch (RuntimeException e) {
            recordFetchFailure();
            log.warn("LangFuse prompt 拉取失败，使用本地 fallback：name={}", name, e);
            return localPromptStore.fetch(name);
        }
    }

    private void recordFetchFailure() {
        if (meterRegistry != null) {
            meterRegistry.counter("kb.prompt.fetch.failure").increment();
        }
    }

    private void recordRenderFailure() {
        if (meterRegistry != null) {
            meterRegistry.counter("kb.prompt.render.failure").increment();
        }
    }

    private RenderedPrompt renderPrompt(CachedPrompt prompt, Map<String, Object> vars) {
        return new RenderedPrompt(prompt.name(), prompt.version(), mustacheLite.render(prompt.rawTemplate(), vars));
    }

    private void recordPromptVersion(CachedPrompt prompt) {
        Map<String, String> current = TracingContextHolder.peek();
        if (current == null) {
            return;
        }
        Map<String, String> updated = new HashMap<>(current);
        updated.put(TracingAttributes.OBSERVATION_PROMPT_NAME, prompt.name());
        updated.put(TracingAttributes.OBSERVATION_PROMPT_VERSION, Integer.toString(prompt.version()));
        TracingContextHolder.set(updated);
    }
}
