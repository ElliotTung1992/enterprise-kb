package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.exception.KbException;
import com.enterprise.kb.search.config.RagasEvalProperties;
import com.enterprise.kb.search.dto.RagasConfig;
import com.enterprise.kb.search.dto.RagasItem;
import com.enterprise.kb.search.dto.RagasResult;
import com.enterprise.kb.search.service.RagasEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 基于 Python evaluation 服务的 Ragas 评估实现。
 */
@Service
@RequiredArgsConstructor
public class RagasEvaluationServiceImpl implements RagasEvaluationService {

    private final RagasEvalProperties properties;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<RagasResult> evaluateBatch(List<RagasItem> items, RagasConfig config) {
        if (!properties.isEnabled()) {
            throw new KbException("Ragas 评估未启用", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (items.isEmpty()) {
            return List.of();
        }

        RestClient client = RestClient.builder().baseUrl(trimTrailingSlash(properties.getEndpoint())).build();
        SubmitResponse submitted = client.post()
                .uri("/evaluations")
                .body(new SubmitRequest(
                        null,
                        items,
                        config.judgeProvider(),
                        config.judgeModel(),
                        config.embeddingProvider(),
                        config.embeddingModel(),
                        config.metrics()))
                .retrieve()
                .body(SubmitResponse.class);
        if (submitted == null || submitted.jobId() == null) {
            throw new KbException("Ragas 评估服务返回无效 jobId", HttpStatus.BAD_GATEWAY);
        }

        Instant deadline = Instant.now().plus(Duration.ofMinutes(properties.getTimeoutMinutes()));
        while (Instant.now().isBefore(deadline)) {
            JobResponse job = client.get()
                    .uri("/evaluations/{jobId}", submitted.jobId())
                    .retrieve()
                    .body(JobResponse.class);
            if (job == null) {
                throw new KbException("Ragas 评估服务返回空响应", HttpStatus.BAD_GATEWAY);
            }
            if ("SUCCEEDED".equals(job.status())) {
                return job.results() == null ? List.of() : job.results();
            }
            if ("FAILED".equals(job.status())) {
                throw new KbException("Ragas 评估失败：" + job.error(), HttpStatus.BAD_GATEWAY);
            }
            sleep();
        }
        throw new KbException("Ragas 评估超时", HttpStatus.GATEWAY_TIMEOUT);
    }

    private void sleep() {
        try {
            Thread.sleep(Math.max(1, properties.getPollIntervalSeconds()) * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KbException("Ragas 评估轮询被中断", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String trimTrailingSlash(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "http://localhost:8000";
        }
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }

    private record SubmitRequest(
            UUID jobId,
            List<RagasItem> items,
            String judgeProvider,
            String judgeModel,
            String embeddingProvider,
            String embeddingModel,
            List<String> metrics
    ) {}

    private record SubmitResponse(UUID jobId, String status, int itemCount) {}

    private record JobResponse(
            UUID jobId,
            String status,
            Progress progress,
            List<RagasResult> results,
            Map<String, Double> summary,
            String error
    ) {}

    private record Progress(int done, int total) {}
}
