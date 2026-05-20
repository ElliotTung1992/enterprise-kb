package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.service.TraceRedactionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

/**
 * Trace 原始载荷脱敏服务实现。
 */
@Service
@RequiredArgsConstructor
public class TraceRedactionServiceImpl implements TraceRedactionService {

    private static final String MASK = "***REDACTED***";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "pwd", "token", "access_token", "refresh_token",
            "authorization", "api_key", "apikey", "secret", "jwt", "credential");

    private final ObjectMapper objectMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public String redactJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            redact(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return json;
        }
    }

    private void redact(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node instanceof ObjectNode objectNode) {
            objectNode.fieldNames().forEachRemaining(fieldName -> {
                JsonNode child = objectNode.get(fieldName);
                if (isSensitive(fieldName)) {
                    objectNode.put(fieldName, MASK);
                } else {
                    redact(child);
                }
            });
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::redact);
        }
    }

    private boolean isSensitive(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_");
        return SENSITIVE_KEYS.contains(normalized)
                || normalized.endsWith("_token")
                || normalized.endsWith("_secret")
                || normalized.endsWith("_key");
    }
}
