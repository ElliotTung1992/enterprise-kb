package com.enterprise.kb.search.mapper;

import com.enterprise.kb.search.model.QaChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 问答会话数据访问 Mapper
 */
@Mapper
public interface QaChatSessionMapper {

    /**
     * 根据 ID 查询会话（含消息数）
     *
     * @param id 会话 ID
     * @return 会话实体（Optional 包装）
     */
    Optional<QaChatSession> findById(@Param("id") UUID id);

    /**
     * 查询指定空间下当前用户的所有未删除会话，按最后活跃时间降序
     *
     * @param spaceId 空间 ID
     * @param userId  用户 ID
     * @return 会话列表（含消息数）
     */
    List<QaChatSession> findBySpaceIdAndUserId(@Param("spaceId") UUID spaceId, @Param("userId") UUID userId);

    /**
     * 插入新会话
     *
     * @param session 会话实体
     */
    void insert(QaChatSession session);

    /**
     * 更新会话标题
     *
     * @param id        会话 ID
     * @param title     新标题
     * @param updatedAt 更新时间
     */
    void updateTitle(@Param("id") UUID id, @Param("title") String title, @Param("updatedAt") Instant updatedAt);

    /**
     * 更新最后活跃时间
     *
     * @param id        会话 ID
     * @param updatedAt 更新时间
     */
    void updateUpdatedAt(@Param("id") UUID id, @Param("updatedAt") Instant updatedAt);

    /**
     * 软删除会话
     *
     * @param id        会话 ID
     * @param deletedAt 删除时间
     */
    void softDelete(@Param("id") UUID id, @Param("deletedAt") Instant deletedAt);

    /**
     * 校验会话归属：会话存在且属于指定用户
     *
     * @param id     会话 ID
     * @param userId 用户 ID
     * @return true 表示归属关系成立
     */
    boolean existsByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);
}
