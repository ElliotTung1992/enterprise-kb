package com.enterprise.kb.search.mapper;

import com.enterprise.kb.search.model.QaChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 问答消息数据访问 Mapper
 */
@Mapper
public interface QaChatMessageMapper {

    /**
     * 查询指定会话的全部消息，按创建时间升序
     *
     * @param sessionId 会话 ID
     * @return 消息列表
     */
    List<QaChatMessage> findBySessionId(@Param("sessionId") UUID sessionId);

    /**
     * 插入单条消息
     *
     * @param message 消息实体
     */
    void insert(QaChatMessage message);

    /**
     * 统计用户跨会话的提问消息数（role='user'，仅未删除会话），用于画像推断去抖。
     *
     * @param userId 用户 ID
     * @return 提问消息总数
     */
    int countUserQuestions(@Param("userId") UUID userId);

    /**
     * 查询用户最近的提问正文（role='user'，仅未删除会话，按时间倒序），供画像推断取样。
     *
     * @param userId 用户 ID
     * @param limit  返回条数上限
     * @return 提问正文列表（最新在前）
     */
    List<String> findRecentUserQuestions(@Param("userId") UUID userId, @Param("limit") int limit);
}
