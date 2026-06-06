package com.enterprise.kb.search.dto;

import java.util.UUID;

/**
 * 一次问答交换已持久化的领域事件。
 *
 * <p>由 {@code QaChatSessionServiceImpl.saveExchange} 在事务内发布，事务提交后触发用户画像统一推断
 * （{@code ProfileActivityListener} 监听 AFTER_COMMIT 并投递 Kafka）。设计见 ADR-016。</p>
 *
 * @param userId 触发本次问答的用户 ID
 */
public record QaExchangeSavedEvent(UUID userId) {
}
