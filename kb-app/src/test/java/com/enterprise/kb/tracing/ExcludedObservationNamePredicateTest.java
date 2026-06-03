package com.enterprise.kb.tracing;

import io.micrometer.observation.Observation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ExcludedObservationNamePredicate} 前缀匹配与空值归一化单元测试
 * （design-langfuse-noise-filter D6）。
 *
 * <p>钉死边界：基础设施噪音被丢、业务链路不被误伤、前缀覆盖后缀、大小写不敏感、
 * 空白规则归一化为放行全部。{@code test} 返回 {@code true}=放行（产 span），
 * {@code false}=NOOP（命中黑名单不产 span）。</p>
 */
class ExcludedObservationNamePredicateTest {

    /** 与 application.yml 默认黑名单一致的规则集。 */
    private static final List<String> DEFAULT_BLACKLIST = List.of(
            "tasks.scheduled.execution",
            "security filterchain",
            "authorize request",
            "authorize method",
            "secured request",
            "http get /**",
            "http get /actuator/health");

    /** predicate 只看 name，context 内容无关，统一传同一个空 context。 */
    private static final Observation.Context CTX = new Observation.Context();

    private final ExcludedObservationNamePredicate predicate =
            new ExcludedObservationNamePredicate(DEFAULT_BLACKLIST);

    @Nested
    @DisplayName("命中黑名单 → NOOP（不产 span）")
    class Excluded {

        @ParameterizedTest
        @ValueSource(strings = {
                "tasks.scheduled.execution",                         // Spring Boot 定时任务 observation name
                "security filterchain before",                       // 前缀盖住 before/after
                "security filterchain after",
                "authorize request",
                "authorize method",
                "secured request",
                "http get /**",
                "http get /actuator/health"
        })
        @DisplayName("已知基础设施噪音被丢")
        void infrastructureNoiseIsExcluded(String name) {
            assertFalse(predicate.test(name, CTX), "应命中黑名单被丢: " + name);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "TASKS.SCHEDULED.EXECUTION",
                "Security FilterChain Before",
                "HTTP GET /actuator/health"
        })
        @DisplayName("匹配大小写不敏感")
        void matchIsCaseInsensitive(String name) {
            assertFalse(predicate.test(name, CTX), "大小写不敏感应命中: " + name);
        }
    }

    @Nested
    @DisplayName("未命中黑名单 → 放行（产 span）")
    class Allowed {

        @ParameterizedTest
        @ValueSource(strings = {
                "kb.qa.ask",
                "kb.retrieval.vector",
                "kb.ingest.document",
                "chat qwen-plus",
                "embedding text-embedding-v2",
                "gen_ai.client.operation",
                "milvus query",
                "http post /api/v1/spaces/{spaceId}/md-qa/ask",        // 业务 http 不被 health 前缀误伤
                "http post /api/v1/spaces/{spaceId}/md-documents/upload"
        })
        @DisplayName("业务链路与未知 observation 放行")
        void businessSpansAreAllowed(String name) {
            assertTrue(predicate.test(name, CTX), "业务链路不应被丢: " + name);
        }

        @Test
        @DisplayName("name 为 null 时放行（不 NPE）")
        void nullNameIsAllowed() {
            assertTrue(predicate.test(null, CTX));
        }
    }

    @Nested
    @DisplayName("空值 / 空白规则归一化 → 放行全部")
    class Normalization {

        @Test
        @DisplayName("规则列表为 null 时放行全部")
        void nullPrefixListAllowsAll() {
            ExcludedObservationNamePredicate p = new ExcludedObservationNamePredicate(null);
            assertTrue(p.test("http get /actuator/health", CTX));
            assertTrue(p.test("kb.qa.ask", CTX));
        }

        @Test
        @DisplayName("规则列表为空时放行全部")
        void emptyPrefixListAllowsAll() {
            ExcludedObservationNamePredicate p = new ExcludedObservationNamePredicate(List.of());
            assertTrue(p.test("security filterchain before", CTX));
        }

        @Test
        @DisplayName("仅空白 / null 规则项被丢弃，等价于放行全部")
        void blankPrefixesAreDiscarded() {
            ExcludedObservationNamePredicate p =
                    new ExcludedObservationNamePredicate(Arrays.asList("  ", "", null, "\t"));
            assertTrue(p.test("http get /actuator/health", CTX));
            assertTrue(p.test("kb.qa.ask", CTX));
        }

        @Test
        @DisplayName("规则项前后空白被 trim 后仍能正确匹配")
        void prefixesAreTrimmed() {
            ExcludedObservationNamePredicate p =
                    new ExcludedObservationNamePredicate(List.of("  http get /actuator/health  "));
            assertFalse(p.test("http get /actuator/health", CTX));
        }
    }
}
