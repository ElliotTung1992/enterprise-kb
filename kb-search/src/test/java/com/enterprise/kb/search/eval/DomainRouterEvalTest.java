package com.enterprise.kb.search.eval;

import com.enterprise.kb.common.constants.Domain;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.dto.ConversationState;
import com.enterprise.kb.search.dto.RoutingDecision;
import com.enterprise.kb.search.service.impl.DomainRouterServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tier-1 域路由器离线评估 harness。
 *
 * <p>读取 {@code intent-eval/frozen-testset.jsonl}，逐 user 轮跑真实 MINIMAX 路由器，
 * 输出域级混淆矩阵 + 记分卡，并按 ADR-008 的门控指标断言（误判域率 &lt; 3%、
 * UNCLEAR &lt; 15%、HANDOFF 漏判 &lt; 5%）。
 *
 * <p>默认跳过（需真实 API Key、产生真实调用费用）。手动运行：
 * <pre>INTENT_EVAL=true MINIMAX_API_KEY=xxx mvn test -pl kb-search -Dtest=DomainRouterEvalTest</pre>
 *
 * <p>注意：门控指标只有在 frozen-testset.jsonl 被运营测试同学填充至目标规模
 * （80~120 段配额覆盖的对话）后才有统计意义；当前仓库内是模板示例。
 */
@EnabledIfEnvironmentVariable(named = "INTENT_EVAL", matches = "true")
class DomainRouterEvalTest {

    private static final Logger log = LoggerFactory.getLogger(DomainRouterEvalTest.class);

    private static final String TESTSET = "intent-eval/frozen-testset.jsonl";
    private static final String SKIP = "SKIP";

    @Test
    void scoreFrozenTestset() throws Exception {
        DomainRouterServiceImpl router = buildRealRouter();
        List<JsonNode> conversations = loadTestset();

        int total = 0;
        int correct = 0;
        int misroute = 0;
        int unclear = 0;
        int handoffExpected = 0;
        int handoffMissed = 0;
        // 混淆矩阵：expected -> (actual -> count)
        Map<String, Map<String, Integer>> matrix = new LinkedHashMap<>();

        for (JsonNode conv : conversations) {
            List<Message> history = new ArrayList<>();
            ConversationState state = ConversationState.initial();

            for (JsonNode turn : conv.get("turns")) {
                String role = turn.get("role").asText();
                String content = turn.get("content").asText();

                if (!"user".equals(role)) {
                    history.add(new AssistantMessage(content));
                    continue;
                }

                JsonNode expNode = turn.get("expected_domain");
                String expected = expNode != null ? expNode.asText() : null;
                // SKIP 轮验证的是分派层 awaiting_slot 硬规则，不计入路由器准确率
                if (expected == null || SKIP.equals(expected)) {
                    history.add(new UserMessage(content));
                    continue;
                }

                RoutingDecision decision = router.route(history, state, content);
                String actual = decision.primaryDomain().name();

                total++;
                if (actual.equals(expected)) {
                    correct++;
                }
                if (Domain.UNCLEAR.name().equals(actual)) {
                    unclear++;
                } else if (!actual.equals(expected)) {
                    misroute++;
                }
                if (Domain.HANDOFF.name().equals(expected)) {
                    handoffExpected++;
                    if (!Domain.HANDOFF.name().equals(actual)) {
                        handoffMissed++;
                    }
                }
                matrix.computeIfAbsent(expected, k -> new LinkedHashMap<>())
                        .merge(actual, 1, Integer::sum);

                if (decision.primaryDomain().isBusinessDomain()) {
                    state = new ConversationState(decision.primaryDomain(), false, 0, null);
                }
                history.add(new UserMessage(content));
            }
        }

        double misrouteRate = total == 0 ? 0 : (double) misroute / total;
        double unclearRate = total == 0 ? 0 : (double) unclear / total;
        double handoffMissRate = handoffExpected == 0 ? 0 : (double) handoffMissed / handoffExpected;

        log.info("\n{}", renderScorecard(total, correct, misroute, unclear,
                misrouteRate, unclearRate, handoffMissRate, matrix));

        assertThat(misrouteRate).as("误判域率").isLessThan(0.03);
        assertThat(unclearRate).as("UNCLEAR 率").isLessThan(0.15);
        assertThat(handoffMissRate).as("HANDOFF 漏判率").isLessThan(0.05);
    }

    // ---- 数据加载 ----

    private List<JsonNode> loadTestset() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<JsonNode> conversations = new ArrayList<>();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(TESTSET)) {
            if (in == null) {
                throw new IllegalStateException("找不到评估集：" + TESTSET);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        conversations.add(mapper.readTree(line));
                    }
                }
            }
        }
        return conversations;
    }

    // ---- 真实路由器构建（镜像 AiModelConfig 的 MINIMAX 装配） ----

    private DomainRouterServiceImpl buildRealRouter() {
        String apiKey = System.getenv("MINIMAX_API_KEY");
        assertThat(apiKey).as("环境变量 MINIMAX_API_KEY").isNotBlank();
        String baseUrl = System.getenv().getOrDefault(
                "MINIMAX_BASE_URL", "https://api.minimax.chat/v1");

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestInterceptor((request, body, execution) -> {
                    var response = execution.execute(request, body);
                    String raw = org.springframework.util.StreamUtils.copyToString(
                            response.getBody(), StandardCharsets.UTF_8);
                    byte[] cleaned = raw.replaceAll("(?s)<think>.*?</think>", "")
                            .getBytes(StandardCharsets.UTF_8);
                    return new org.springframework.http.client.ClientHttpResponse() {
                        @Override public InputStream getBody() {
                            return new java.io.ByteArrayInputStream(cleaned);
                        }
                        @Override public org.springframework.http.HttpHeaders getHeaders() {
                            return response.getHeaders();
                        }
                        @Override public org.springframework.http.HttpStatusCode getStatusCode()
                                throws java.io.IOException {
                            return response.getStatusCode();
                        }
                        @Override public String getStatusText() throws java.io.IOException {
                            return response.getStatusText();
                        }
                        @Override public void close() {
                            response.close();
                        }
                    };
                });

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder)
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model("MiniMax-M2.7-highspeed").build())
                .build();
        ChatClient minimax = ChatClient.builder(chatModel).build();

        ModelProviderResolver resolver = new ModelProviderResolver(null, null, minimax, null);
        DomainRouterServiceImpl router = new DomainRouterServiceImpl(resolver);
        ReflectionTestUtils.setField(router, "routerProvider", "MINIMAX");
        ReflectionTestUtils.setField(router, "contextTurns", 3);
        return router;
    }

    // ---- 记分卡渲染 ----

    private String renderScorecard(int total, int correct, int misroute, int unclear,
                                   double misrouteRate, double unclearRate, double handoffMissRate,
                                   Map<String, Map<String, Integer>> matrix) {
        StringBuilder sb = new StringBuilder();
        sb.append("===== Tier-1 域路由评估记分卡 =====\n");
        sb.append(String.format("样本总数（不含 SKIP 轮）：%d%n", total));
        sb.append(String.format("准确：%d（%.1f%%）%n", correct, pct(correct, total)));
        sb.append(String.format("误判域率：%.1f%%  目标 < 3%%  %s%n",
                misrouteRate * 100, misrouteRate < 0.03 ? "PASS" : "FAIL"));
        sb.append(String.format("UNCLEAR 率：%.1f%%  目标 < 15%%  %s%n",
                unclearRate * 100, unclearRate < 0.15 ? "PASS" : "FAIL"));
        sb.append(String.format("HANDOFF 漏判率：%.1f%%  目标 < 5%%  %s%n",
                handoffMissRate * 100, handoffMissRate < 0.05 ? "PASS" : "FAIL"));
        sb.append("\n混淆矩阵（行=期望，列=实际）：\n");
        for (Map.Entry<String, Map<String, Integer>> row : matrix.entrySet()) {
            sb.append(String.format("  %-12s -> %s%n", row.getKey(), row.getValue()));
        }
        return sb.toString();
    }

    private double pct(int n, int total) {
        return total == 0 ? 0 : 100.0 * n / total;
    }
}
