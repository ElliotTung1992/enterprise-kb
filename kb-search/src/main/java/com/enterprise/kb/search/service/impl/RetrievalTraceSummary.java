package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.dto.SearchHit;

import java.util.List;
import java.util.Locale;

/**
 * 把检索命中折叠成进 trace 的结构化摘要（design-langfuse-io-mapping D5）。
 *
 * <p>只写 {@code score / documentTitle / section / excerpt} 这类排障必需字段，
 * <b>不</b>写完整 parent 正文：每条 excerpt 截到 {@link #EXCERPT_PREVIEW_CHARS} 预览长度，
 * 整体长度再由调用方按 {@code max-retrieval-chars} 二次截断。正文的脱敏由调用方
 * （{@code TracingSupport.outputFrom}）统一收口。</p>
 */
final class RetrievalTraceSummary {

    /** 每条命中 excerpt 进 trace 的预览字符数，避免单条长 excerpt 吃满检索预算。 */
    private static final int EXCERPT_PREVIEW_CHARS = 200;

    private RetrievalTraceSummary() {
    }

    /**
     * 将命中列表折叠成 {@code {"hitCount":N,"hits":[{score,documentTitle,section,excerpt}...]}} 摘要。
     *
     * @param hits 命中列表（可为空）
     * @return JSON 风格摘要字符串
     */
    static String summarize(List<SearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "{\"hitCount\":0,\"hits\":[]}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"hitCount\":").append(hits.size()).append(",\"hits\":[");
        for (int i = 0; i < hits.size(); i++) {
            SearchHit hit = hits.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"score\":").append(String.format(Locale.ROOT, "%.4f", hit.score()))
                    .append(",\"documentTitle\":\"").append(escape(hit.documentTitle()))
                    .append("\",\"section\":\"").append(escape(hit.section()))
                    .append("\",\"excerpt\":\"").append(escape(preview(hit.excerpt())))
                    .append("\"}");
        }
        return sb.append("]}").toString();
    }

    private static String preview(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= EXCERPT_PREVIEW_CHARS ? text : text.substring(0, EXCERPT_PREVIEW_CHARS) + "…";
    }

    private static String escape(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
