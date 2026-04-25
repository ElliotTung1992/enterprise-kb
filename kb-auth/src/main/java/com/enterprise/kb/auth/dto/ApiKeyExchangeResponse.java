package com.enterprise.kb.auth.dto;

public record ApiKeyExchangeResponse(String accessToken, String tokenType, long expiresIn) {
    public ApiKeyExchangeResponse(String accessToken, long expiresIn) {
        this(accessToken, "Bearer", expiresIn);
    }
}
