package com.enterprise.kb.user.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.user.dto.CreateMcpApiKeyRequest;
import com.enterprise.kb.user.dto.McpApiKeyDto;
import com.enterprise.kb.user.service.McpApiKeyService;
import com.enterprise.kb.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/api-keys")
@RequiredArgsConstructor
public class McpApiKeyController {

    private final McpApiKeyService mcpApiKeyService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateMcpApiKeyRequest request) {
        UUID userId = userService.getUserIdByUsername(userDetails.getUsername());
        McpApiKeyService.McpApiKeyCreatedResult result = mcpApiKeyService.createApiKey(userId, request);
        // rawKey 仅此一次返回
        return ResponseEntity.status(201).body(ApiResponse.ok(
                Map.of("key", result.rawKey(), "meta", result.dto()),
                "API key created. Save the key now — it will not be shown again."));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<McpApiKeyDto>>> list(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userService.getUserIdByUsername(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(mcpApiKeyService.listApiKeys(userId)));
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<ApiResponse<Void>> revoke(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID keyId) {
        UUID userId = userService.getUserIdByUsername(userDetails.getUsername());
        mcpApiKeyService.revokeApiKey(keyId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null, "API key revoked"));
    }
}
