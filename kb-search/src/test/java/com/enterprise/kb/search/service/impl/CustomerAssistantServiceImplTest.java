package com.enterprise.kb.search.service.impl;

import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.enterprise.kb.common.exception.KbException;
import com.enterprise.kb.common.prompt.PromptProvider;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.ai.RedisChatMemory;
import com.enterprise.kb.search.dto.CustomerMessageDto;
import com.enterprise.kb.search.dto.CustomerSessionDto;
import com.enterprise.kb.search.mapper.CustomerMessageMapper;
import com.enterprise.kb.search.mapper.CustomerSessionMapper;
import com.enterprise.kb.search.model.CustomerSession;
import com.enterprise.kb.search.service.ReviewRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAssistantServiceImplTest {

    @Mock ModelProviderResolver modelProviderResolver;
    @Mock RedisChatMemory redisChatMemory;
    @Mock RedisSaver agentCheckpointSaver;
    @Mock ReviewRequestService reviewRequestService;
    @Mock CustomerSessionMapper customerSessionMapper;
    @Mock CustomerMessageMapper customerMessageMapper;
    @Mock ObjectMapper objectMapper;
    @Mock PromptProvider promptProvider;

    @InjectMocks CustomerAssistantServiceImpl service;

    // -- behavior 1: listSessions maps CustomerSession to CustomerSessionDto --

    @Test
    void listSessionsMapsSessionFieldsToDto() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-01-02T12:00:00Z");

        CustomerSession session = new CustomerSession();
        session.setId(sessionId);
        session.setTitle("我要退款");
        session.setMessageCount(4);
        session.setCreatedAt(createdAt);
        session.setUpdatedAt(updatedAt);

        when(customerSessionMapper.findByUserId(userId)).thenReturn(List.of(session));

        List<CustomerSessionDto> result = service.listSessions(userId);

        assertThat(result).hasSize(1);
        CustomerSessionDto dto = result.get(0);
        assertThat(dto.id()).isEqualTo(sessionId);
        assertThat(dto.title()).isEqualTo("我要退款");
        assertThat(dto.messageCount()).isEqualTo(4);
        assertThat(dto.createdAt()).isEqualTo(createdAt);
        assertThat(dto.updatedAt()).isEqualTo(updatedAt);
    }

    // -- behavior 2: getMessages throws KbException when session doesn't belong to user --

    @Test
    void getMessagesThrowsKbExceptionWhenSessionNotOwnedByUser() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(customerSessionMapper.existsByIdAndUserId(sessionId, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.getMessages(sessionId, userId))
                .isInstanceOf(KbException.class);
    }

    // -- behavior 3: getMessages returns message list when session belongs to user --

    @Test
    void getMessagesReturnsDtosForAuthorizedUser() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CustomerMessageDto msg1 = new CustomerMessageDto(UUID.randomUUID(), "user", "你好", "AFTER_SALES", Instant.now());
        CustomerMessageDto msg2 = new CustomerMessageDto(UUID.randomUUID(), "assistant", "您好，请问有什么可以帮您？", null, Instant.now());

        when(customerSessionMapper.existsByIdAndUserId(sessionId, userId)).thenReturn(true);
        when(customerMessageMapper.findBySessionId(sessionId)).thenReturn(List.of(msg1, msg2));

        List<CustomerMessageDto> result = service.getMessages(sessionId, userId);

        assertThat(result).containsExactly(msg1, msg2);
    }

    // -- behavior 4: deleteSession calls softDelete on mapper and clear on Redis cache --

    @Test
    void deleteSessionSoftDeletesRecordAndClearsRedisHistory() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ArgumentCaptor<String> redisKeyCaptor = ArgumentCaptor.forClass(String.class);

        service.deleteSession(sessionId, userId);

        verify(customerSessionMapper).softDelete(eq(sessionId), eq(userId), any(Instant.class));
        verify(redisChatMemory).clear(redisKeyCaptor.capture());
        assertThat(redisKeyCaptor.getValue()).isEqualTo(sessionId.toString());
    }
}
