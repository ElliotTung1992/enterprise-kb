package com.enterprise.kb.auth.mapper;

import com.enterprise.kb.auth.model.RefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 刷新令牌数据访问 Mapper 接口
 */
@Mapper
public interface RefreshTokenMapper {

    /**
     * 根据 tokenHash 查询刷新令牌
     *
     * @param tokenHash 令牌哈希值
     * @return 刷新令牌（Optional 包装）
     */
    Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 插入刷新令牌
     *
     * @param refreshToken 令牌实体
     */
    void insert(RefreshToken refreshToken);

    /**
     * 更新刷新令牌
     *
     * @param refreshToken 令牌实体
     */
    void update(RefreshToken refreshToken);

    /**
     * 撤销指定用户的所有未撤销令牌
     *
     * @param userId 用户 ID
     * @param now    当前时间
     */
    void revokeAllByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * 删除已过期的令牌
     *
     * @param cutoff 截止时间
     */
    void deleteExpired(@Param("cutoff") Instant cutoff);

    /**
     * 删除用户的所有令牌
     *
     * @param userId 用户 ID
     */
    void deleteByUserId(@Param("userId") UUID userId);
}
