package com.enterprise.kb.user.mapper;

import com.enterprise.kb.user.model.McpApiKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface McpApiKeyMapper {

    Optional<McpApiKey> findById(@Param("id") UUID id);

    Optional<UUID> findUserIdByKeyHash(@Param("keyHash") String keyHash);

    List<McpApiKey> findActiveByUserId(@Param("userId") UUID userId);

    void insert(McpApiKey key);

    void softDelete(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * 删除用户的所有 API Key（硬删除）
     *
     * @param userId 用户 ID
     */
    void deleteByUserId(@Param("userId") UUID userId);
}
