package com.enterprise.kb.tracing;

import com.enterprise.kb.common.tracing.SensitiveDataRedactor;
import com.enterprise.kb.common.tracing.TracingAttributes;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 将 Spring AI VectorStore observation 的查询请求 / 召回结果映射到 LangFuse 原生属性。
 *
 * <p>Spring AI 的 VectorStore observation（{@code milvus query} 等）只把查询文本写进语义约定属性
 * {@code db.vector.query.content}，召回的文档结果则完全不进 trace；二者都不会变成
 * {@code langfuse.observation.input/output}，因此 LangFuse 的 input/output 列恒为空。本过滤器
 * 在 observation stop 前从 {@link VectorStoreObservationContext} 读取
 * {@link SearchRequest} 与召回 {@link Document} 列表，写入 LangFuse 可识别的高基数 KeyValue，
 * 并复用项目统一脱敏截断规则。</p>
 *
 * <p>与 {@link ChatModelContentObservationFilter} / {@link ToolCallingContentLangfuseObservationFilter}
 * 共用同一套 LangFuse 语义，补齐 ChatModel / Tool / 自建 kb.* span 之外缺失的 VectorStore 这一类。</p>
 */
public class VectorStoreContentObservationFilter implements ObservationFilter {

    private final int maxChars;

    /**
     * @param maxChars 查询 / 召回正文截断上限（复用 max-retrieval-chars）
     */
    public VectorStoreContentObservationFilter(int maxChars) {
        this.maxChars = maxChars;
    }

    @Override
    public Observation.Context map(Observation.Context context) {
        if (!(context instanceof VectorStoreObservationContext vectorContext)) {
            return context;
        }

        String input = requestText(vectorContext.getQueryRequest());
        if (hasText(input)) {
            context.addHighCardinalityKeyValue(KeyValue.of(
                    TracingAttributes.OBSERVATION_INPUT,
                    SensitiveDataRedactor.redactAndTruncate(input, maxChars)));
        }

        String output = responseText(vectorContext.getQueryResponse());
        if (hasText(output)) {
            context.addHighCardinalityKeyValue(KeyValue.of(
                    TracingAttributes.OBSERVATION_OUTPUT,
                    SensitiveDataRedactor.redactAndTruncate(output, maxChars)));
        }

        return context;
    }

    /**
     * 把查询请求拼成可读文本：查询语句 + topK + 相似度阈值 + 过滤表达式。
     */
    private String requestText(SearchRequest request) {
        if (request == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (hasText(request.getQuery())) {
            sb.append("query: ").append(request.getQuery());
        }
        sb.append("\ntopK: ").append(request.getTopK());
        sb.append("\nsimilarityThreshold: ").append(request.getSimilarityThreshold());
        if (request.hasFilterExpression() && request.getFilterExpression() != null) {
            sb.append("\nfilter: ").append(request.getFilterExpression());
        }
        return sb.toString();
    }

    /**
     * 把召回文档列表拼成可读文本：命中数 + 逐条 score / 元数据 / 正文摘录。
     */
    private String responseText(List<Document> documents) {
        if (documents == null) {
            return "";
        }
        if (documents.isEmpty()) {
            return "hitCount: 0";
        }
        String hits = IntStream.range(0, documents.size())
                .mapToObj(i -> documentLine(i + 1, documents.get(i)))
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
        return "hitCount: " + documents.size() + "\n" + hits;
    }

    private String documentLine(int rank, Document document) {
        if (document == null) {
            return null;
        }
        Map<String, Object> metadata = document.getMetadata();
        Object section = metadata == null ? null : metadata.get("section");
        return String.join("\n",
                "- rank: " + rank,
                "  id: " + nullToEmpty(document.getId()),
                "  score: " + (document.getScore() == null ? "" : document.getScore()),
                "  section: " + nullToEmpty(section == null ? null : section.toString()),
                "  text: " + nullToEmpty(document.getText()));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
