package com.enterprise.kb.search.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class QaChatMessage {

    private UUID id;
    /** 所属会话 ID */
    private UUID sessionId;
    /** 角色（user / assistant） */
    private String role;
    /** 消息正文 */
    private String content;
    /** 创建时间 */
    private Instant createdAt;
}
