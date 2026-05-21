package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.model.EvalCase;
import com.enterprise.kb.search.model.EvalRun;
import com.enterprise.kb.search.model.EvalRunResult;
import com.enterprise.kb.search.service.EvalCaseService;
import com.enterprise.kb.search.service.EvalReplayService;
import com.enterprise.kb.search.service.EvalRunService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 最小离线评估回放服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalReplayServiceImpl implements EvalReplayService {

    private final EvalCaseService evalCaseService;
    private final EvalRunService evalRunService;
    private final ObjectMapper objectMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public EvalRun runDataset(String dataset) {
        List<EvalCase> cases = evalCaseService.listEnabledByDataset(dataset);
        EvalRun run = new EvalRun();
        run.setDataset(dataset);
        run.setStatus("RUNNING");
        run.setConfigJson(jsonOf(Map.of("dataset", dataset, "caseCount", cases.size())));
        run.setSummaryJson("{}");
        run = evalRunService.create(run);

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
}
