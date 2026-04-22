package com.enterprise.kb.auth.dto;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UUID userId,
        String username,
        String email
) {
    public AuthResponse(String accessToken, String refreshToken, long expiresIn,
                        UUID userId, String username, String email) {
        this(accessToken, refreshToken, "Bearer", expiresIn, userId, username, email);
    }
}
