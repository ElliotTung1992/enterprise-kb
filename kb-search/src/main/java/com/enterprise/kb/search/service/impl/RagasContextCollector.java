package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.dto.SearchHit;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ragas 评估期间收集答案依据文本。
 */
@Component
public class RagasContextCollector {

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    /**
     * 获取当前线程中的收集 scope。
     *
     * @return 当前 scope
     */
    public static Optional<Scope> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * 打开收集 scope。
     *
     * @return scope
     */
    public Scope openScope() {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("RagasContextCollector scope 已存在，不可嵌套");
        }
        Scope scope = new Scope();
        CURRENT.set(scope);
        return scope;
    }

    /**
     * 单次评估收集 scope。
     */
    public static final class Scope implements AutoCloseable {
        private final Map<String, String> contextsByKey = new LinkedHashMap<>();

        /**
         * 记录检索命中。
         *
         * @param hit 检索命中
         */
        public void recordHit(SearchHit hit) {
            if (hit == null || hit.excerpt() == null || hit.excerpt().isBlank()) {
                return;
            }
            String key = hit.chunkId() != null ? hit.chunkId() : hit.documentId() + ":" + contextsByKey.size();
            contextsByKey.putIfAbsent(key, hit.excerpt());
        }

        /**
         * 批量记录检索命中。
         *
         * @param hits 检索命中列表
         */
        public void recordHits(List<SearchHit> hits) {
            if (hits == null) {
                return;
            }
            hits.forEach(this::recordHit);
        }

        /**
         * 获取去重后的答案依据文本。
         *
         * @return contexts
         */
        public List<String> snapshot() {
            return List.copyOf(contextsByKey.values());
        }

        @Override
        public void close() {
            CURRENT.remove();
        }
    }
}
