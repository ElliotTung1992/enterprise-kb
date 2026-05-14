package com.enterprise.kb.search.model;

import com.enterprise.kb.common.constants.ComplaintStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class Complaint {

    private UUID id;
    /** 投诉用户 ID */
    private UUID userId;
    /** 关联订单号 */
    private String orderId;
    /** 用户投诉原文 */
    private String content;
    /** 投诉案件状态 */
    private ComplaintStatus status;
    /** 创建时间 */
    private Instant createdAt;
    /** 最后更新时间 */
    private Instant updatedAt;
    /** 结案时间，null 表示尚未结案 */
    private Instant resolvedAt;
}
