package com.enterprise.kb.search.mapper;

import com.enterprise.kb.search.model.CustomerSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 商城客户助手会话 Mapper
 */
@Mapper
public interface CustomerSessionMapper {

    /**
     * 查询指定用户的未删除会话列表，按最后活跃时间降序，含消息数
     *
     * @param userId 用户 ID
     * @return 会话列表
     */
    List<CustomerSession> findByUserId(@Param("userId") UUID userId);

    /**
     * 会话是否存在（未删除）
     *
     * @param id     会话 ID
     * @param userId 用户 ID
     * @return true 表示存在且属于该用户
     */
    boolean existsByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * 插入新会话，会话 ID 已存在时忽略（ON CONFLICT DO NOTHING）。
     * 用于首次消息时的幂等会话创建，避免并发重复插入。
     *
     * @param session 会话实体
     */
    void insertIfAbsent(CustomerSession session);

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
     * @param userId    用户 ID（防止越权）
     * @param deletedAt 删除时间
     */
    void softDelete(@Param("id") UUID id, @Param("userId") UUID userId,
                    @Param("deletedAt") Instant deletedAt);
}
