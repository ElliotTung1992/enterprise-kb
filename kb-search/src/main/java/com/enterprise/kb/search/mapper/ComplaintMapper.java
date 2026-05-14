package com.enterprise.kb.search.mapper;

import com.enterprise.kb.common.constants.ComplaintStatus;
import com.enterprise.kb.search.model.Complaint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 投诉升级案件 Mapper
 */
@Mapper
public interface ComplaintMapper {

    /**
     * 根据 ID 查询投诉案件
     *
     * @param id 投诉案件 ID
     * @return 投诉案件
     */
    Optional<Complaint> findById(@Param("id") UUID id);

    /**
     * 插入投诉案件
     *
     * @param complaint 投诉案件
     */
    void insert(Complaint complaint);

    /**
     * 更新投诉案件状态
     *
     * @param id        投诉案件 ID
     * @param status    新状态
     * @param updatedAt 更新时间
     */
    void updateStatus(@Param("id") UUID id,
                      @Param("status") ComplaintStatus status,
                      @Param("updatedAt") Instant updatedAt);
}
