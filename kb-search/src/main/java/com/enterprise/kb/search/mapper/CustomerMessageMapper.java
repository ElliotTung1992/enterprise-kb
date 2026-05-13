package com.enterprise.kb.search.mapper;

import com.enterprise.kb.search.dto.CustomerMessageDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 商城客户助手消息 Mapper
 */
@Mapper
public interface CustomerMessageMapper {

    /**
     * 查询指定会话的所有消息，按时间升序
     *
     * @param sessionId 会话 ID
     * @return 消息列表
     */
    List<CustomerMessageDto> findBySessionId(@Param("sessionId") UUID sessionId);

    /**
     * 插入一条消息
     *
     * @param sessionId 会话 ID
     * @param role      角色（user / assistant）
     * @param content   消息正文
     */
    void insert(@Param("sessionId") UUID sessionId, @Param("role") String role,
                @Param("content") String content);
}
