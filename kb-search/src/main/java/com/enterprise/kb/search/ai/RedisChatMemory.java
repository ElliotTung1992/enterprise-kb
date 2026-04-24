package com.enterprise.kb.search.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于 Redis 的对话历史存储，实现 Spring AI {@link ChatMemory} 接口。
 * <p>每个会话以 {@code qa:session:{conversationId}} 为 key，将消息列表序列化为 JSON 存储，
 * TTL 24 小时，每次写入时刷新过期时间。写入时保留最近 {@value #HISTORY_WINDOW} 条，
 * 防止历史无限增长。仅保留 user / assistant 两种角色消息。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatMemory implements ChatMemory {

    private static final String KEY_PREFIX = "qa:session:";
    private static final Duration SESSION_TTL = Duration.ofHours(24);
    /** 每个会话最多保留的消息条数（user + assistant 各算 1 条） */
    private static final int HISTORY_WINDOW = 20;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 追加消息到指定会话，超出 {@value #HISTORY_WINDOW} 条时截断最早的记录。
     *
     * @param conversationId 会话 ID
     * @param messages       待追加的消息列表
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        String key = KEY_PREFIX + conversationId;
        try {
            List<Map<String, String>> history = loadRaw(key);
            for (Message msg : messages) {
                history.add(Map.of(
                        "role", msg.getMessageType().getValue(),
                        "content", msg.getText()
                ));
            }
            // 超出窗口时移除最早的记录
            if (history.size() > HISTORY_WINDOW) {
                history = history.subList(history.size() - HISTORY_WINDOW, history.size());
            }
            stringRedisTemplate.opsForValue().set(
                    key, objectMapper.writeValueAsString(history), SESSION_TTL);
            log.debug("保存会话消息，sessionId={}，当前消息总数={}", conversationId, history.size());
        } catch (Exception e) {
            log.warn("保存会话历史失败，sessionId={}: {}", conversationId, e.getMessage());
        }
    }

    /**
     * 读取指定会话的全部历史消息。
     *
     * @param conversationId 会话 ID
     * @return 消息列表，读取失败时返回空列表
     */
    @Override
    public List<Message> get(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        try {
            return loadRaw(key).stream().map(m -> {
                String content = m.getOrDefault("content", "");
                if ("assistant".equals(m.get("role"))) return (Message) new AssistantMessage(content);
                return (Message) new UserMessage(content);
            }).toList();
        } catch (Exception e) {
            log.warn("加载会话历史失败，sessionId={}: {}", conversationId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 清空指定会话的所有消息。
     *
     * @param conversationId 会话 ID
     */
    @Override
    public void clear(String conversationId) {
        stringRedisTemplate.delete(KEY_PREFIX + conversationId);
        log.debug("清空会话历史，sessionId={}", conversationId);
    }

    private List<Map<String, String>> loadRaw(String key) throws Exception {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) return new ArrayList<>();
        return objectMapper.readValue(json, new TypeReference<>() {});
    }
}
