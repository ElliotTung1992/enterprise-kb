package com.enterprise.kb.search.mapper;

import com.enterprise.kb.common.constants.ReviewStatus;
import com.enterprise.kb.search.model.ReviewRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 审核申请数据访问 Mapper
 */
@Mapper
public interface ReviewRequestMapper {

    /**
     * 根据 ID 查询审核申请
     *
     * @param id 申请 ID
     * @return 申请实体（Optional 包装）
     */
    Optional<ReviewRequest> findById(@Param("id") UUID id);

    /**
     * 查询指定空间下待审核的申请列表，按创建时间升序
     *
     * @param spaceId 空间 ID
     * @return 待审核申请列表
     */
    List<ReviewRequest> findPendingBySpaceId(@Param("spaceId") UUID spaceId);

    /**
     * 查询所有待审核的申请列表（含客户助手申请），按创建时间升序
     *
     * @return 所有待审核申请列表
     */
    List<ReviewRequest> findAllPending();

    /**
     * 按状态查询申请列表，status 为 null 时返回全部，按创建时间降序
     *
     * @param status 状态过滤（可为 null）
     * @return 申请列表
     */
    List<ReviewRequest> findByStatus(@Nullable @Param("status") ReviewStatus status);

    /**
     * 插入新审核申请
     *
     * @param reviewRequest 申请实体
     */
    void insert(ReviewRequest reviewRequest);

    /**
     * 更新审核决策（通过或拒绝）
     *
     * @param id             申请 ID
     * @param status         新状态（APPROVED / REJECTED）
     * @param reviewerId     审核员 ID
     * @param reviewerComment 审核意见
     * @param updatedAt      更新时间
     * @param decidedAt      决策时间
     */
    void updateDecision(@Param("id") UUID id,
                        @Param("status") ReviewStatus status,
                        @Param("reviewerId") UUID reviewerId,
                        @Param("reviewerComment") String reviewerComment,
                        @Param("updatedAt") Instant updatedAt,
                        @Param("decidedAt") Instant decidedAt);
}
