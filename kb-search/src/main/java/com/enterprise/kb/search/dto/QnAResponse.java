package com.enterprise.kb.search.dto;

import java.util.List;
import java.util.UUID;

public record QnAResponse(
        String answer,
        UUID sessionId,
        List<Citation> citations,
        String modelUsed,
        int tokensUsed
) {}
