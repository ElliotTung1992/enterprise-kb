package com.enterprise.kb.search.ai;

import com.enterprise.kb.common.constants.Domain;
import com.enterprise.kb.search.dto.ConversationState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ConversationStateStore} 单测：Redis 路由状态的存取往返与容错降级。
 * <p>Redis 边界用 mock，序列化用真实 {@link ObjectMapper}——JSON 往返是本单元的真实行为。</p>
 */
@ExtendWith(MockitoExtension.class)
class ConversationStateStoreTest {

    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private ConversationStateStore store;

    @BeforeEach
    void setUp() {
        store = new ConversationStateStore(stringRedisTemplate, new ObjectMapper());
    }

    @Test
    void savesAndLoadsStateRoundTrip() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        ConversationState state = new ConversationState(Domain.AFTER_SALES, true, 2, Domain.COMPLAINT);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        store.save("s1", state);
        verify(valueOps).set(eq("qa:state:s1"), json.capture(), any(Duration.class));

        when(valueOps.get("qa:state:s1")).thenReturn(json.getValue());
        assertThat(store.load("s1")).isEqualTo(state);
    }

    @Test
    void loadReturnsInitialWhenKeyAbsent() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("qa:state:missing")).thenReturn(null);

        assertThat(store.load("missing")).isEqualTo(ConversationState.initial());
    }

    @Test
    void loadReturnsInitialOnCorruptJson() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("{not valid json");

        assertThat(store.load("s1")).isEqualTo(ConversationState.initial());
    }

    @Test
    void clearDeletesStateKey() {
        store.clear("s1");

        verify(stringRedisTemplate).delete("qa:state:s1");
    }

    @Test
    void saveSwallowsRedisFailure() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        doThrow(new RuntimeException("redis down"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        assertThatCode(() -> store.save("s1", ConversationState.initial()))
                .doesNotThrowAnyException();
    }
}
