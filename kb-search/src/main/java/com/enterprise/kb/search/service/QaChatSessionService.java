package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.QaChatMessageDto;
import com.enterprise.kb.search.dto.QaChatSessionDto;

import java.util.List;
import java.util.UUID;

/**
 * 问答会话管理服务接口
 * <p>负责会话元数据的增删改查，以及问答交互消息的持久化。
 * 会话创建由 {@code saveExchange} 在首次问答时隐式触发，无需单独创建接口。</p>
 */
public interface QaChatSessionService {

    /**
     * 查询当前用户在指定空间下的所有会话（按最后活跃时间倒序）
     *
     * @param spaceId 空间 ID
     * @param userId  用户 ID
     * @return 会话列表（含消息数）
     */
    List<QaChatSessionDto> listSessions(UUID spaceId, UUID userId);

    /**
     * 查询指定会话的历史消息
     *
     * @param sessionId 会话 ID
     * @param userId    请求用户 ID（用于鉴权）
     * @return 消息列表（按时间升序）
     */
    List<QaChatMessageDto> getMessages(UUID sessionId, UUID userId);

    /**
     * 持久化一轮问答交互：首次调用时自动创建会话，后续调用追加消息并更新活跃时间
     *
     * @param sessionId 会话 ID
     * @param spaceId   空间 ID
     * @param userId    用户 ID
     * @param question  用户问题
     * @param answer    AI 回答
     */
    void saveExchange(UUID sessionId, UUID spaceId, UUID userId, String question, String answer);

    /**
     * 修改会话标题
     *
     * @param sessionId 会话 ID
     * @param userId    请求用户 ID（用于鉴权）
     * @param title     新标题
     */
    void renameSession(UUID sessionId, UUID userId, String title);

    /**
     * 软删除会话并清空对应的 Redis 对话历史
     *
     * @param sessionId 会话 ID
     * @param userId    请求用户 ID（用于鉴权）
     */
    void deleteSession(UUID sessionId, UUID userId);
}
