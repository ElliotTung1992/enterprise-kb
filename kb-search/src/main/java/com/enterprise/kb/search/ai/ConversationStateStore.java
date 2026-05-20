package com.enterprise.kb.search.ai;

import com.enterprise.kb.search.dto.ConversationState;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis 的会话路由状态存储。
 *
 * <p>每个会话以 {@code qa:state:{sessionId}} 为 key，存储 {@link ConversationState}
 * 的 JSON 序列化结果，TTL 24 小时（与 {@link RedisChatMemory} 的对话历史生命周期一致）。
 *
 * <p>职责边界：本存储只持有"易变的路由输入态"（当前域、槽位等待标记、情绪计数）。
 * 持久审计轨迹（每条消息归属的域）写在 PostgreSQL {@code customer_messages.domain} 列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationStateStore {

    private static final String KEY_PREFIX = "qa:state:";
    private static final Duration STATE_TTL = Duration.ofHours(24);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 读取指定会话的路由状态。
     *
     * @param sessionId 会话 ID
     * @return 路由状态；不存在或读取失败时返回 {@link ConversationState#initial()}
     */
    public ConversationState load(String sessionId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
            if (json == null || json.isBlank()) {
                return ConversationState.initial();
            }
            return objectMapper.readValue(json, ConversationState.class);
        } catch (Exception e) {
            log.warn("加载会话路由状态失败，sessionId={}: {}", sessionId, e.getMessage());
            return ConversationState.initial();
        }
    }

    /**
     * 保存指定会话的路由状态，刷新 TTL。
     *
     * @param sessionId 会话 ID
     * @param state     待保存的路由状态
     */
    public void save(String sessionId, ConversationState state) {
        try {
            stringRedisTemplate.opsForValue().set(
                    KEY_PREFIX + sessionId, objectMapper.writeValueAsString(state), STATE_TTL);
        } catch (Exception e) {
            log.warn("保存会话路由状态失败，sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 清除指定会话的路由状态。
     *
     * @param sessionId 会话 ID
     */
    public void clear(String sessionId) {
        stringRedisTemplate.delete(KEY_PREFIX + sessionId);
    }
}
