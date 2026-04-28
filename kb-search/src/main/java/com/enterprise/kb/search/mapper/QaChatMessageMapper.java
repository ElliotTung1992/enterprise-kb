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
}
