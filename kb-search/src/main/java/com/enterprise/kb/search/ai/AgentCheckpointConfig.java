package com.enterprise.kb.search.ai;

import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent Checkpoint 配置：使用 Redis 持久化 ReactAgent 图状态，支持 HITL 中断后的恢复。
 * <p>RedisSaver 依赖 RedissonClient（与 Spring Data Redis 使用不同客户端），需单独创建。</p>
 */
@Configuration
public class AgentCheckpointConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * Redisson 客户端，专供 RedisSaver 使用，不影响 Spring Data Redis 的 RedisTemplate。
     *
     * @return RedissonClient 实例
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = "redis://" + redisHost + ":" + redisPort;
        var serverConfig = config.useSingleServer()
                .setAddress(address)
                .setConnectionPoolSize(4)
                .setConnectionMinimumIdleSize(1);
        if (redisPassword != null && !redisPassword.isBlank()) {
            serverConfig.setPassword(redisPassword);
        }
        return Redisson.create(config);
    }

    /**
     * RedisSaver：ReactAgent Checkpoint 存储，用于 HITL 中断时持久化图状态。
     * <p>threadId = sessionId，对应 QA 会话 ID。</p>
     *
     * @param redissonClient Redisson 客户端
     * @return RedisSaver 实例
     */
    @Bean
    public RedisSaver agentCheckpointSaver(RedissonClient redissonClient) {
        return RedisSaver.builder().redisson(redissonClient).build();
    }
}
