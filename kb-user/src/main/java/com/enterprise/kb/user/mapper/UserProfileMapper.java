package com.enterprise.kb.user.mapper;

import com.enterprise.kb.user.model.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * 用户画像数据访问 Mapper 接口。
 */
@Mapper
public interface UserProfileMapper {

    /**
     * 根据用户 ID 查询画像。
     *
     * @param userId 用户 ID
     * @return 画像实体（Optional 包装，不存在则为空）
     */
    Optional<UserProfile> findByUserId(@Param("userId") UUID userId);

    /**
     * 新增或更新画像（按 user_id 冲突时整体覆盖 profile 与 updated_at）。
     *
     * @param profile 画像实体
     */
    void upsert(UserProfile profile);
}
