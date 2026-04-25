package com.enterprise.kb.user.service.impl;

import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.user.dto.CreateMcpApiKeyRequest;
import com.enterprise.kb.user.dto.McpApiKeyDto;
import com.enterprise.kb.user.mapper.McpApiKeyMapper;
import com.enterprise.kb.user.model.McpApiKey;
import com.enterprise.kb.user.service.McpApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class McpApiKeyServiceImpl implements McpApiKeyService {

    private final McpApiKeyMapper mcpApiKeyMapper;

    @Override
    @Transactional
    public McpApiKeyCreatedResult createApiKey(UUID userId, CreateMcpApiKeyRequest request) {
        String rawKey = generateRawKey();
        String prefix = "ekb_" + rawKey.substring(0, 8);

        McpApiKey key = new McpApiKey();
        key.setId(UUID.randomUUID());
        key.setUserId(userId);
        key.setKeyHash(hash(rawKey));
        key.setKeyPrefix(prefix);
        key.setName(request.name());
        key.setExpiresAt(request.expiresAt());
        mcpApiKeyMapper.insert(key);

        return new McpApiKeyCreatedResult(toDto(key), rawKey);
    }

    @Override
    @Transactional(readOnly = true)
    public List<McpApiKeyDto> listApiKeys(UUID userId) {
        return mcpApiKeyMapper.findActiveByUserId(userId).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public void revokeApiKey(UUID keyId, UUID userId) {
        mcpApiKeyMapper.findById(keyId)
                .filter(k -> k.getUserId().equals(userId) && k.isActive())
                .orElseThrow(() -> new ResourceNotFoundException("McpApiKey", keyId));
        mcpApiKeyMapper.softDelete(keyId, userId);
    }

    private String generateRawKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest(value.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private McpApiKeyDto toDto(McpApiKey k) {
        return new McpApiKeyDto(k.getId(), k.getKeyPrefix(), k.getName(), k.getExpiresAt(), k.getCreatedAt());
    }
}
