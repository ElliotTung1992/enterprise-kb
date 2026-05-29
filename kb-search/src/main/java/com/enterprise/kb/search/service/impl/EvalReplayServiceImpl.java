package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.exception.KbException;
import com.enterprise.kb.search.config.RagasEvalProperties;
import com.enterprise.kb.search.dto.EvalRunRequest;
import com.enterprise.kb.search.dto.QnARequest;
import com.enterprise.kb.search.dto.QnAResponse;
import com.enterprise.kb.search.dto.RagasConfig;
import com.enterprise.kb.search.dto.RagasItem;
import com.enterprise.kb.search.dto.RagasResult;
import com.enterprise.kb.search.model.EvalCase;
import com.enterprise.kb.search.model.EvalRun;
import com.enterprise.kb.search.model.EvalRunResult;
import com.enterprise.kb.search.service.EvalCaseService;
import com.enterprise.kb.search.service.EvalReplayService;
import com.enterprise.kb.search.service.EvalRunService;
import com.enterprise.kb.search.service.MdAgenticQnAService;
import com.enterprise.kb.search.service.MdQnAService;
import com.enterprise.kb.search.service.RagasEvaluationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 离线评估回放服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalReplayServiceImpl implements EvalReplayService {

    private final EvalCaseService evalCaseService;
    private final EvalRunService evalRunService;
    private final RagasEvaluationService ragasEvaluationService;
    private final RagasEvalProperties ragasProperties;
    private final RagasContextCollector ragasContextCollector;
    private final MdQnAService mdQnAService;
    private final MdAgenticQnAService mdAgenticQnAService;
    private final ObjectMapper objectMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public EvalRun runDataset(String dataset) {
        return runStaticAssertions(dataset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EvalRun run(EvalRunRequest request) {
        // 校验参数
        if (request == null || request.dataset() == null || request.dataset().isBlank()) {
            throw new KbException("dataset 不能为空", HttpStatus.BAD_REQUEST);
        }
        // 测评RAG
        if (EvalType.RAGAS.matches(request.type())) {
            return runRagas(request);
        }
        // 测评QA
        return runStaticAssertions(request.dataset());
    }

    private EvalRun runStaticAssertions(String dataset) {
        List<EvalCase> cases = evalCaseService.listEnabledByDataset(dataset);
        EvalRun run = createRun(dataset, jsonOf(Map.of("dataset", dataset, "caseCount", cases.size())));

        int passed = 0;
        int failed = 0;
        for (EvalCase evalCase : cases) {
            AssertionResult assertion = assertCase(evalCase);
            EvalRunResult result = new EvalRunResult();
            result.setEvalRunId(run.getId());
            result.setEvalCaseId(evalCase.getId());
            result.setStatus(assertion.passed() ? "PASSED" : "FAILED");
            result.setActualJson(assertion.actualJson());
            result.setAssertionResultJson(jsonOf(Map.of(
                    "passed", assertion.passed(),
                    "reason", assertion.reason())));
            result.setFailureReason(assertion.passed() ? null : assertion.reason());
            evalRunService.saveResult(result);
            if (assertion.passed()) {
                passed++;
            } else {
                failed++;
            }
        }

        String status = failed == 0 ? "SUCCEEDED" : "FAILED";
        String summary = jsonOf(new LinkedHashMap<>(Map.of(
                "total", cases.size(),
                "passed", passed,
                "failed", failed,
                "passRate", cases.isEmpty() ? 1.0d : passed * 1.0d / cases.size())));
        evalRunService.complete(run.getId(), status, summary);
        return evalRunService.findById(run.getId()).orElse(run);
    }

    private EvalRun runRagas(EvalRunRequest request) {
        // 查询测试案例
        List<EvalCase> cases = evalCaseService.listEnabledByDataset(request.dataset());
        RagasConfig config = toRagasConfig(request);
        // 阀值
        Map<String, Double> thresholds = thresholds(request);
        // 生成评测案例
        EvalRun run = createRun(request.dataset(), jsonOf(configJson(request, cases.size(), config, thresholds)));

        List<RagasPreparedItem> preparedItems = new ArrayList<>();
        for (EvalCase evalCase : cases) {
            try {
                preparedItems.add(prepareRagasItem(evalCase, request));
            } catch (Exception e) {
                log.warn("Ragas 评估用例执行失败：caseId={}", evalCase.getId(), e);
                saveFailedResult(run, evalCase, "执行目标 RAG 服务失败：" + e.getMessage());
            }
        }

        List<RagasResult> scoredItems;
        try {
            scoredItems = ragasEvaluationService.evaluateBatch(
                    preparedItems.stream().map(RagasPreparedItem::item).toList(), config);
        } catch (Exception e) {
            log.warn("Ragas 批量评估失败：runId={}", run.getId(), e);
            String summary = jsonOf(new LinkedHashMap<>(Map.of(
                    "total", cases.size(),
                    "passed", 0,
                    "failed", cases.size(),
                    "passRate", 0.0d,
                    "error", e.getMessage())));
            evalRunService.complete(run.getId(), "FAILED", summary);
            return evalRunService.findById(run.getId()).orElse(run);
        }
        Map<UUID, RagasPreparedItem> preparedByCaseId = preparedItems.stream()
                .collect(Collectors.toMap(i -> i.item().caseId(), Function.identity()));

        int passed = 0;
        int failed = cases.size() - preparedItems.size();
        Map<String, Integer> violations = new LinkedHashMap<>();
        Map<String, Double> totals = new LinkedHashMap<>();
        for (RagasResult scoredItem : scoredItems) {
            RagasPreparedItem prepared = preparedByCaseId.get(scoredItem.caseId());
            if (prepared == null) {
                continue;
            }
            ThresholdResult thresholdResult = evaluateThresholds(scoredItem.scores(), thresholds);
            thresholdResult.violations().forEach((metric, count) -> violations.merge(metric, count, Integer::sum));
            scoredItem.scores().forEach((metric, score) -> totals.merge(metric, score, Double::sum));

            EvalRunResult result = new EvalRunResult();
            result.setEvalRunId(run.getId());
            result.setEvalCaseId(scoredItem.caseId());
            result.setStatus(thresholdResult.passed() ? "PASSED" : "FAILED");
            result.setActualJson(jsonOf(actualJson(scoredItem, prepared)));
            result.setAssertionResultJson(jsonOf(Map.of(
                    "passed", thresholdResult.passed(),
                    "thresholds", thresholds,
                    "violations", thresholdResult.violations())));
            result.setFailureReason(thresholdResult.passed() ? null : "Ragas 指标低于阈值");
            evalRunService.saveResult(result);
            if (thresholdResult.passed()) {
                passed++;
            } else {
                failed++;
            }
        }

        Map<String, Double> averages = new LinkedHashMap<>();
        int resultCount = Math.max(1, scoredItems.size());
        totals.forEach((metric, total) -> averages.put(metric, total / resultCount));
        String status = "warn".equalsIgnoreCase(ragasProperties.getGateMode()) || failed == 0 ? "SUCCEEDED" : "FAILED";
        String summary = jsonOf(new LinkedHashMap<>(Map.of(
                "total", cases.size(),
                "passed", passed,
                "failed", failed,
                "passRate", cases.isEmpty() ? 1.0d : passed * 1.0d / cases.size(),
                "ragasAverages", averages,
                "thresholdViolations", violations,
                "gateMode", ragasProperties.getGateMode())));
        evalRunService.complete(run.getId(), status, summary);
        return evalRunService.findById(run.getId()).orElse(run);
    }

    private EvalRun createRun(String dataset, String configJson) {
        EvalRun run = new EvalRun();
        run.setDataset(dataset);
        run.setStatus("RUNNING");
        run.setConfigJson(configJson);
        run.setSummaryJson("{}");
        return evalRunService.create(run);
    }

    // 组装发送给RAGAS的数据
    private RagasPreparedItem prepareRagasItem(EvalCase evalCase, EvalRunRequest request) throws java.io.IOException {
        JsonNode input = readTree(evalCase.getInputJson());
        JsonNode expected = readTree(evalCase.getExpectedJson());
        UUID spaceId = UUID.fromString(required(input, "spaceId"));
        String question = required(input, "question");
        RagasMode mode = RagasMode.from(input.path("mode").asText(null), request.targetService());
        String modelProvider = input.path("modelProvider").asText(null);
        String modelName = input.path("modelName").asText(null);
        int topK = input.path("topK").asInt(5);
        QnARequest qnARequest = new QnARequest(question, UUID.randomUUID(), modelProvider, modelName, topK);

        Instant startedAt = Instant.now();
        QnAResponse response;
        List<String> contexts;
        try (RagasContextCollector.Scope scope = ragasContextCollector.openScope()) {
            response = mode == RagasMode.AGENTIC
                    ? mdAgenticQnAService.ask(spaceId, qnARequest)
                    : mdQnAService.ask(spaceId, qnARequest);
            contexts = scope.snapshot();
        }
        long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
        String groundTruth = expected.path("referenceAnswer").asText(expected.path("groundTruth").asText(null));
        RagasItem item = new RagasItem(evalCase.getId(), question, response.answer(), contexts, groundTruth);
        Map<String, Object> evaluationInput = new LinkedHashMap<>();
        evaluationInput.put("question", question);
        evaluationInput.put("answer", response.answer());
        evaluationInput.put("contexts", contexts);
        evaluationInput.put("groundTruth", groundTruth);
        evaluationInput.put("answerLatencyMs", latencyMs);
        evaluationInput.put("mode", mode.name());
        evaluationInput.put("modelProvider", response.modelUsed());
        return new RagasPreparedItem(item, evaluationInput);
    }

    private String required(JsonNode input, String field) {
        String value = input.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new KbException("eval_cases.input_json 缺少必填字段：" + field, HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private RagasConfig toRagasConfig(EvalRunRequest request) {
        return new RagasConfig(
                firstNonBlank(request.judgeProvider(), ragasProperties.getJudgeProvider()),
                firstNonBlank(request.judgeModel(), ragasProperties.getJudgeModel()),
                firstNonBlank(request.embeddingProvider(), ragasProperties.getEmbeddingProvider()),
                firstNonBlank(request.embeddingModel(), ragasProperties.getEmbeddingModel()),
                request.metrics() == null || request.metrics().isEmpty()
                        ? ragasProperties.getMetrics()
                        : request.metrics());
    }

    private Map<String, Double> thresholds(EvalRunRequest request) {
        Map<String, Double> thresholds = new LinkedHashMap<>(ragasProperties.getThresholds());
        if (request.thresholds() != null) {
            thresholds.putAll(request.thresholds());
        }
        return thresholds;
    }

    private Map<String, Object> configJson(EvalRunRequest request, int caseCount,
                                           RagasConfig config, Map<String, Double> thresholds) {
        Map<String, Object> ragas = new LinkedHashMap<>();
        ragas.put("judgeProvider", config.judgeProvider());
        ragas.put("judgeModel", config.judgeModel());
        ragas.put("embeddingProvider", config.embeddingProvider());
        ragas.put("embeddingModel", config.embeddingModel());
        ragas.put("metricsEnabled", config.metrics());
        ragas.put("targetService", request.targetService());
        ragas.put("gateMode", ragasProperties.getGateMode());
        ragas.put("thresholds", thresholds);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("evalType", EvalType.RAGAS.name());
        root.put("dataset", request.dataset());
        root.put("caseCount", caseCount);
        root.put("ragas", ragas);
        return root;
    }

    private ThresholdResult evaluateThresholds(Map<String, Double> scores, Map<String, Double> thresholds) {
        Map<String, Integer> violations = new LinkedHashMap<>();
        if (scores == null || scores.isEmpty()) {
            return new ThresholdResult(false, Map.of("missingScores", 1));
        }
        for (Map.Entry<String, Double> entry : thresholds.entrySet()) {
            Double score = scores.get(entry.getKey());
            if (score != null && score < entry.getValue()) {
                violations.put(entry.getKey(), 1);
            }
        }
        return new ThresholdResult(violations.isEmpty(), violations);
    }

    private Map<String, Object> actualJson(RagasResult result, RagasPreparedItem prepared) {
        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("ragasScores", result.scores());
        actual.put("breakdown", result.breakdown());
        actual.put("evaluationInput", prepared.evaluationInput());
        return actual;
    }

    private void saveFailedResult(EvalRun run, EvalCase evalCase, String reason) {
        EvalRunResult result = new EvalRunResult();
        result.setEvalRunId(run.getId());
        result.setEvalCaseId(evalCase.getId());
        result.setStatus("FAILED");
        result.setActualJson("{}");
        result.setAssertionResultJson(jsonOf(Map.of("passed", false, "reason", reason)));
        result.setFailureReason(reason);
        evalRunService.saveResult(result);
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private AssertionResult assertCase(EvalCase evalCase) {
        try {
            JsonNode input = readTree(evalCase.getInputJson());
            JsonNode expected = readTree(evalCase.getExpectedJson());
            String actualText = input.path("answer").asText(input.path("message").asText(""));
            Map<String, Object> actual = new LinkedHashMap<>();
            actual.put("caseType", evalCase.getCaseType());
            actual.put("text", actualText);
            actual.put("mocked", true);

            for (JsonNode term : expected.path("mustContain")) {
                if (!actualText.contains(term.asText())) {
                    return new AssertionResult(false, "缺少必须包含文本：" + term.asText(), jsonOf(actual));
                }
            }
            for (JsonNode term : expected.path("mustNotContain")) {
                if (actualText.contains(term.asText())) {
                    return new AssertionResult(false, "包含禁止文本：" + term.asText(), jsonOf(actual));
                }
            }
            String expectedDomain = expected.path("domain").asText(null);
            if (expectedDomain != null && !expectedDomain.equals(input.path("domain").asText(null))) {
                return new AssertionResult(false, "路由域不匹配，期望：" + expectedDomain, jsonOf(actual));
            }
            return new AssertionResult(true, "断言通过", jsonOf(actual));
        } catch (Exception e) {
            log.warn("评估用例断言失败：caseId={}", evalCase.getId(), e);
            return new AssertionResult(false, "断言执行异常：" + e.getMessage(), "{}");
        }
    }

    private JsonNode readTree(String json) throws java.io.IOException {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(json);
    }

    private String jsonOf(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private record AssertionResult(boolean passed, String reason, String actualJson) {}

    private record RagasPreparedItem(RagasItem item, Map<String, Object> evaluationInput) {}

    private record ThresholdResult(boolean passed, Map<String, Integer> violations) {}

    private enum EvalType {
        RAGAS;

        private boolean matches(String value) {
            return value != null && name().equalsIgnoreCase(value);
        }
    }

    private enum RagasMode {
        SINGLE,
        AGENTIC;

        private static RagasMode from(String value, String targetService) {
            if (value == null || value.isBlank()) {
                return "MdAgenticQnAService".equals(targetService) ? AGENTIC : SINGLE;
            }
            for (RagasMode mode : values()) {
                if (mode.name().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            throw new KbException("不支持的 Ragas mode：" + value, HttpStatus.BAD_REQUEST);
        }
    }
}
