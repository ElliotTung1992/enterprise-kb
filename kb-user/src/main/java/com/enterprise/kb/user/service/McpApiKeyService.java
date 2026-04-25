package com.enterprise.kb.user.service;

import com.enterprise.kb.user.dto.CreateMcpApiKeyRequest;
import com.enterprise.kb.user.dto.McpApiKeyDto;

import java.util.List;
import java.util.UUID;

public interface McpApiKeyService {

    /**
     * 为指定用户创建新的 API Key，返回 DTO（含一次性明文 key）。
     * rawKey 仅在此处返回一次，不存储明文。
     */
    McpApiKeyCreatedResult createApiKey(UUID userId, CreateMcpApiKeyRequest request);

    List<McpApiKeyDto> listApiKeys(UUID userId);

    void revokeApiKey(UUID keyId, UUID userId);

    record McpApiKeyCreatedResult(McpApiKeyDto dto, String rawKey) {}
}
