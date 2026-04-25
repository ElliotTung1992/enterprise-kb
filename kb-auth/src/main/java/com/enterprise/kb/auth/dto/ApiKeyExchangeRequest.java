package com.enterprise.kb.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ApiKeyExchangeRequest(@NotBlank String apiKey) {}
