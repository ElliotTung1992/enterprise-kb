package com.enterprise.kb.user.mapper;

import com.enterprise.kb.user.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户数据访问 Mapper 接口
 */
@Mapper
public interface UserMapper {

    /**
     * 根据 ID 查询用户
     *
     * @param id 用户 ID
     * @return 用户实体（Optional 包装）
     */
    Optional<User> findById(@Param("id") UUID id);

    /**
     * 根据用户名查询未删除用户
     *
     * @param username 用户名
     * @return 用户实体（Optional 包装）
     */
    Optional<User> findByUsernameAndDeletedAtIsNull(@Param("username") String username);

    /**
     * 根据邮箱查询未删除用户
     *
     * @param email 邮箱
     * @return 用户实体（Optional 包装）
     */
    Optional<User> findByEmailAndDeletedAtIsNull(@Param("email") String email);

    /**
     * 判断用户名是否已被未删除用户使用
     *
     * @param username 用户名
     * @return true 表示已存在
     */
    boolean existsByUsernameAndDeletedAtIsNull(@Param("username") String username);

    /**
     * 判断邮箱是否已被未删除用户使用
     *
     * @param email 邮箱
     * @return true 表示已存在
     */
    boolean existsByEmailAndDeletedAtIsNull(@Param("email") String email);

    /**
     * 分页查询未删除用户（支持关键词过滤）
     *
     * @param keyword 关键词（可为 null）
     * @return 用户列表（由 PageHelper 截断）
     */
    List<User> findAllActive(@Param("keyword") String keyword);

    /**
     * 插入用户
     *
     * @param user 用户实体
     */
    void insert(User user);

    /**
     * 更新用户
     *
     * @param user 用户实体
     */
    void update(User user);
}
