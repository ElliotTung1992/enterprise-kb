package com.enterprise.kb.tracing;

import com.enterprise.kb.common.tracing.TracingAttributes;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VectorStoreContentObservationFilterTest {

    @Test
    void mapsQueryRequestAndResponseToLangfuseObservationAttributes() {
        VectorStoreObservationContext context = queryContext(
                SearchRequest.builder()
                        .query("退款流程是什么？")
                        .topK(20)
                        .similarityThreshold(0.0)
                        .filterExpression("spaceId == 'space-1'")
                        .build(),
                List.of(
                        Document.builder()
                                .id("doc-1")
                                .text("请按工单流程提交退款申请。")
                                .score(0.92)
                                .metadata(Map.of("section", "售后 > 退款"))
                                .build()));

        new VectorStoreContentObservationFilter(2000).map(context);

        assertThat(highCardinalityValue(context, TracingAttributes.OBSERVATION_INPUT))
                .contains("query: 退款流程是什么？")
                .contains("topK: 20")
                .contains("similarityThreshold: 0.0")
                .contains("filter:");
        assertThat(highCardinalityValue(context, TracingAttributes.OBSERVATION_OUTPUT))
                .contains("hitCount: 1")
                .contains("id: doc-1")
                .contains("score: 0.92")
                .contains("section: 售后 > 退款")
                .contains("text: 请按工单流程提交退款申请。");
    }

    @Test
    void writesExplicitZeroHitCountWhenResponseEmpty() {
        VectorStoreObservationContext context = queryContext(
                SearchRequest.builder().query("无命中查询").topK(5).build(),
                List.of());

        new VectorStoreContentObservationFilter(2000).map(context);

        assertThat(highCardinalityValue(context, TracingAttributes.OBSERVATION_OUTPUT))
                .isEqualTo("hitCount: 0");
    }

    @Test
    void ignoresNonVectorStoreObservationContext() {
        Observation.Context context = new Observation.Context();

        new VectorStoreContentObservationFilter(2000).map(context);

        assertThat(context.getHighCardinalityKeyValues()).isEmpty();
    }

    @Test
    void redactsSensitiveContentInQueryAndResponse() {
        VectorStoreObservationContext context = queryContext(
                SearchRequest.builder().query("api_key: sk-1234567890ABCDEFGHIJK 退款").topK(1).build(),
                List.of(Document.builder()
                        .id("doc-1")
                        .text("token: Bearer abcdefghijklmnopqrstuvwxyz")
                        .score(0.5)
                        .build()));

        new VectorStoreContentObservationFilter(2000).map(context);

        assertThat(highCardinalityValue(context, TracingAttributes.OBSERVATION_INPUT))
                .contains("[REDACTED]")
                .doesNotContain("sk-1234567890ABCDEFGHIJK");
        assertThat(highCardinalityValue(context, TracingAttributes.OBSERVATION_OUTPUT))
                .contains("[REDACTED]")
                .doesNotContain("abcdefghijklmnopqrstuvwxyz");
    }

    @Test
    void truncatesOversizedContent() {
        String longText = "退款流程".repeat(200);
        VectorStoreObservationContext context = queryContext(
                SearchRequest.builder().query("退款").topK(1).build(),
                List.of(Document.builder().id("doc-1").text(longText).score(0.5).build()));

        new VectorStoreContentObservationFilter(50).map(context);

        assertThat(highCardinalityValue(context, TracingAttributes.OBSERVATION_OUTPUT))
                .contains("truncated, originalLength=");
    }

    private VectorStoreObservationContext queryContext(SearchRequest request, List<Document> response) {
        VectorStoreObservationContext context =
                new VectorStoreObservationContext.Builder("milvus", VectorStoreObservationContext.Operation.QUERY.value())
                        .collectionName("md_kb_chunks")
                        .queryRequest(request)
                        .build();
        context.setQueryResponse(response);
        return context;
    }

    private String highCardinalityValue(Observation.Context context, String key) {
        return context.getHighCardinalityKeyValues().stream()
                .filter(kv -> key.equals(kv.getKey()))
                .map(KeyValue::getValue)
                .findFirst()
                .orElse(null);
    }
}
