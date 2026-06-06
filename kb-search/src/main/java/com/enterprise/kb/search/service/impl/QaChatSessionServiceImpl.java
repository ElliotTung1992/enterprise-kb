package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.exception.KbException;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.search.ai.RedisChatMemory;
import com.enterprise.kb.search.dto.QaChatMessageDto;
import com.enterprise.kb.search.dto.QaChatSessionDto;
import com.enterprise.kb.search.dto.QaExchangeSavedEvent;
import com.enterprise.kb.search.mapper.QaChatMessageMapper;
import com.enterprise.kb.search.mapper.QaChatSessionMapper;
import com.enterprise.kb.search.model.QaChatMessage;
import com.enterprise.kb.search.model.QaChatSession;
import com.enterprise.kb.search.service.QaChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 问答会话管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaChatSessionServiceImpl implements QaChatSessionService {

    /** 会话标题最大字符数 */
    private static final int TITLE_MAX_LENGTH = 50;

    private final QaChatSessionMapper sessionMapper;
    private final QaChatMessageMapper messageMapper;
    private final RedisChatMemory redisChatMemory;
    /** 发布问答持久化事件，事务提交后触发用户画像离线推断（ADR-016）。 */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<QaChatSessionDto> listSessions(UUID spaceId, UUID userId) {
        return sessionMapper.findBySpaceIdAndUserId(spaceId, userId).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<QaChatMessageDto> getMessages(UUID sessionId, UUID userId) {
        if (!sessionMapper.existsByIdAndUserId(sessionId, userId)) {
            throw new ResourceNotFoundException("QaChatSession", sessionId);
        }
        return messageMapper.findBySessionId(sessionId).stream()
                .map(m -> new QaChatMessageDto(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void saveExchange(UUID sessionId, UUID spaceId, UUID userId, String question, String answer) {
        Instant now = Instant.now();
        boolean sessionExists = sessionMapper.findById(sessionId).isPresent();

        if (!sessionExists) {
            QaChatSession session = new QaChatSession();
            session.setId(sessionId);
            session.setSpaceId(spaceId);
            session.setUserId(userId);
            session.setTitle(buildTitle(question));
            session.setCreatedAt(now);
            session.setUpdatedAt(now);
            sessionMapper.insert(session);
            log.debug("创建新会话：sessionId={}", sessionId);
        } else {
            sessionMapper.updateUpdatedAt(sessionId, now);
        }

        QaChatMessage userMsg = buildMessage(sessionId, "user", question, now);
        // 保证用户消息在助手消息之前，创建时间偏移 1 毫秒
        QaChatMessage assistantMsg = buildMessage(sessionId, "assistant", answer, now.plusMillis(1));

        messageMapper.insert(userMsg);
        messageMapper.insert(assistantMsg);

        // 事务提交后触发画像离线推断（监听器在 AFTER_COMMIT 投递 Kafka）；离线推断关闭时无监听器、事件被丢弃。
        eventPublisher.publishEvent(new QaExchangeSavedEvent(userId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void renameSession(UUID sessionId, UUID userId, String title) {
        if (!sessionMapper.existsByIdAndUserId(sessionId, userId)) {
            throw new ResourceNotFoundException("QaChatSession", sessionId);
        }
        sessionMapper.updateTitle(sessionId, title.strip(), Instant.now());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteSession(UUID sessionId, UUID userId) {
        if (!sessionMapper.existsByIdAndUserId(sessionId, userId)) {
            throw new ResourceNotFoundException("QaChatSession", sessionId);
        }
        sessionMapper.softDelete(sessionId, Instant.now());
        redisChatMemory.clear(sessionId.toString());
        log.info("删除会话：sessionId={}", sessionId);
    }

    // ---- private helpers ----

    private QaChatSessionDto toDto(QaChatSession s) {
        return new QaChatSessionDto(s.getId(), s.getSpaceId(), s.getTitle(),
                s.getMessageCount(), s.getCreatedAt(), s.getUpdatedAt());
    }

    private String buildTitle(String question) {
        String trimmed = question.strip();
        return trimmed.length() > TITLE_MAX_LENGTH
                ? trimmed.substring(0, TITLE_MAX_LENGTH) + "…"
                : trimmed;
    }

    private QaChatMessage buildMessage(UUID sessionId, String role, String content, Instant createdAt) {
        QaChatMessage msg = new QaChatMessage();
        msg.setId(UUID.randomUUID());
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setCreatedAt(createdAt);
        return msg;
    }
}
