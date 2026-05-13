package com.enterprise.kb.search.service;

import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.enterprise.kb.search.dto.CustomerAssistantRequest;
import com.enterprise.kb.search.dto.CustomerAssistantResponse;
import com.enterprise.kb.search.dto.CustomerMessageDto;
import com.enterprise.kb.search.dto.CustomerSessionDto;

import java.util.List;
import java.util.UUID;

/**
 * 商城客户助手服务接口。
 * <p>独立于知识库问答，面向 C 端用户提供售后咨询对话能力：
 * 查询订单资格、提交人工审核申请（HITL）、接收审核结果通知。</p>
 */
public interface CustomerAssistantService {

    /**
     * 发送消息并获取 AI 回复
     * <p>若 sessionId 为 null 则自动创建新会话。
     * 若触发 HITL 中断（提交售后审核），立即返回"申请已提交"响应。</p>
     *
     * @param req 对话请求
     * @return AI 回复及会话 ID
     */
    CustomerAssistantResponse chat(CustomerAssistantRequest req);

    /**
     * 审核完成后恢复被 HITL 中断的 Agent
     * <p>审核员批准或拒绝后调用此方法，Agent 从 Redis checkpoint 恢复执行，
     * 结果写入 customer_messages，用户下次打开会话可查看。</p>
     *
     * @param sessionId            被中断的会话 ID
     * @param interruptionMetadata 含审核决策的中断元数据
     * @return 恢复后的 AI 回复
     */
    CustomerAssistantResponse resumeWithFeedback(UUID sessionId, InterruptionMetadata interruptionMetadata);

    /**
     * 查询当前用户的会话列表（按最后活跃时间倒序）
     *
     * @param userId 用户 ID
     * @return 会话列表（含消息数）
     */
    List<CustomerSessionDto> listSessions(UUID userId);

    /**
     * 查询指定会话的历史消息
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID（用于归属校验）
     * @return 消息列表（按时间升序）
     */
    List<CustomerMessageDto> getMessages(UUID sessionId, UUID userId);

    /**
     * 软删除会话并清空对应的 Redis 对话历史
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID（防止越权）
     */
    void deleteSession(UUID sessionId, UUID userId);
}
