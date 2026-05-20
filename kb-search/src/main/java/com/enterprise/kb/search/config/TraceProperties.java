package com.enterprise.kb.search.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent Trace 配置属性。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "enterprise.kb.trace")
public class TraceProperties {

    /** 是否启用 Trace 采集 */
    private boolean enabled = true;
    /** 是否记录完整原始载荷 */
    private boolean fullRawEnabled = true;
    /** 采样率，1.0 表示全量 */
    private double sampleRate = 1.0;
    /** 单个 JSON payload 最大字节数，超出后截断 */
    private int maxPayloadBytes = 262144;
    /** 是否记录历史消息 */
    private boolean includeHistory = true;
    /** 是否记录 system prompt 和工具描述 */
    private boolean includePrompts = true;
    /** 是否记录检索片段正文 */
    private boolean includeRetrievalExcerpts = true;
}
