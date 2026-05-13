package com.enterprise.kb.search.service;

import com.enterprise.kb.common.constants.ReviewStatus;
import com.enterprise.kb.search.model.ReviewRequest;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 审核申请服务接口
 */
public interface ReviewRequestService {

    /**
     * 创建待审核申请
     *
     * @param sessionId            关联会话 ID（同时作为 ReactAgent threadId）
     * @param spaceId              知识空间 ID
     * @param userId               申请用户 ID
     * @param orderId              订单号
     * @param reason               用户诉求（AI 提炼）
     * @param conversationSnapshot 申请时的对话快照（JSON 字符串）
     * @param orderDetails         订单详情（JSON 字符串）
     * @param toolCallId           HITL 中断时 LLM 工具调用 ID，resume 时重建 InterruptionMetadata 使用
     * @param toolCallName         被拦截的工具名称
     * @return 创建的审核申请
     */
    ReviewRequest createPending(UUID sessionId, UUID spaceId, UUID userId,
                                String orderId, String reason,
                                String conversationSnapshot, String orderDetails,
                                String toolCallId, String toolCallName);

    /**
     * 根据 ID 查询审核申请（不限状态）
     *
     * @param id 申请 ID
     * @return 审核申请实体
     */
    ReviewRequest findById(UUID id);

    /**
     * 查询指定空间下待审核的申请列表
     *
     * @param spaceId 知识空间 ID
     * @return 待审核申请列表
     */
    List<ReviewRequest> listPending(UUID spaceId);

    /**
     * 查询所有待审核的申请列表（包含客户助手提交的、不绑定空间的申请）
     *
     * @return 所有待审核申请列表
     */
    List<ReviewRequest> listAllPending();

    /**
     * 按状态查询申请列表，status 为 null 时返回全部，按创建时间降序
     *
     * @param status 状态过滤（可为 null）
     * @return 申请列表
     */
    List<ReviewRequest> listByStatus(@Nullable ReviewStatus status);

    /**
     * 审核通过
     *
     * @param id             申请 ID
     * @param reviewerId     审核员用户 ID
     * @param comment        审核意见
     * @return 已更新的审核申请（含 sessionId、toolCallId 供调用方恢复 Agent 执行）
     */
    ReviewRequest approve(UUID id, UUID reviewerId, String comment);

    /**
     * 审核拒绝
     *
     * @param id         申请 ID
     * @param reviewerId 审核员用户 ID
     * @param comment    拒绝原因
     * @return 已更新的审核申请（含 sessionId、toolCallId 供调用方恢复 Agent 执行）
     */
    ReviewRequest reject(UUID id, UUID reviewerId, String comment);
}
