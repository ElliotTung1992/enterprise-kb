package com.enterprise.kb.auth.service.impl;

import com.enterprise.kb.auth.dto.*;
import com.enterprise.kb.auth.mapper.RefreshTokenMapper;
import com.enterprise.kb.auth.model.RefreshToken;
import com.enterprise.kb.auth.service.AuthService;
import com.enterprise.kb.auth.service.JwtService;
import com.enterprise.kb.common.exception.KbException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final JwtService jwtService;
    private final RefreshTokenMapper refreshTokenMapper;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final BiFunction<String, Object, UUID> userCreator;
    private final Function<String, UUID> userIdLookup;
    private final BiConsumer<UUID, String> passwordHashUpdater;

    @Value("${enterprise.kb.jwt.refresh-token-expiry-days:30}")
    private long refreshTokenExpiryDays;

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        UserDetails userDetails = (UserDetails) auth.getPrincipal();
        UUID userId = userIdLookup.apply(userDetails.getUsername());
        return buildAuthResponse(userDetails, userId);
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        UUID userId = userCreator.apply(request.username(), request);
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        return buildAuthResponse(userDetails, userId);
    }

    @Override
    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String tokenHash = hash(rawRefreshToken);
        RefreshToken stored = refreshTokenMapper.findByTokenHash(tokenHash)
                .orElseThrow(() -> new KbException("Invalid refresh token", HttpStatus.UNAUTHORIZED));
        if (!stored.isValid())
            throw new KbException("Refresh token expired or revoked", HttpStatus.UNAUTHORIZED);
        stored.setRevokedAt(Instant.now());
        refreshTokenMapper.update(stored);
        UserDetails userDetails = userDetailsService.loadUserByUsername(jwtService.extractUsername(rawRefreshToken));
        return buildAuthResponse(userDetails, stored.getUserId());
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        String tokenHash = hash(rawRefreshToken);
        refreshTokenMapper.findByTokenHash(tokenHash).ifPresent(t -> {
            t.setRevokedAt(Instant.now());
            refreshTokenMapper.update(t);
        });
    }

    @Override
    public void changePassword(String username, String currentPassword, String newPassword) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (!passwordEncoder.matches(currentPassword, userDetails.getPassword())) {
            throw new KbException("Current password is incorrect", HttpStatus.BAD_REQUEST);
        }
        UUID userId = userIdLookup.apply(username);
        passwordHashUpdater.accept(userId, passwordEncoder.encode(newPassword));
    }

    private AuthResponse buildAuthResponse(UserDetails userDetails, UUID userId) {
        String accessToken = jwtService.generateAccessToken(userDetails, userId);
        String rawRefresh = generateRawRefreshToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(UUID.randomUUID());
        refreshToken.setUserId(userId);
        refreshToken.setTokenHash(hash(rawRefresh));
        refreshToken.setExpiresAt(Instant.now().plus(refreshTokenExpiryDays, ChronoUnit.DAYS));
        refreshTokenMapper.insert(refreshToken);
        return new AuthResponse(accessToken, rawRefresh, jwtService.getAccessTokenExpirySeconds(),
                userId, userDetails.getUsername(), null);
    }

    private String generateRawRefreshToken() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest(value.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
