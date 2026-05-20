package com.enterprise.kb.search.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ConversationState} 单测。
 */
class ConversationStateTest {

    @Test
    void initialStateIsEmpty() {
        ConversationState s = ConversationState.initial();

        assertThat(s.currentDomain()).isNull();
        assertThat(s.awaitingSlot()).isFalse();
        assertThat(s.emotionStrikes()).isZero();
        assertThat(s.pendingOffer()).isNull();
    }
}
