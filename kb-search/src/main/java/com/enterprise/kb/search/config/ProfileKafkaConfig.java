package com.enterprise.kb.search.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 用户画像离线推断的 Kafka 配置（ADR-016，Phase 2）。
 *
 * <p>仅当 {@code enterprise.kb.profile.inference.enabled=true} 时装配：声明用户活动事件 topic，
 * 并提供 Kafka 不可用时的降级线程池。关闭时本类不装配，应用无需 Kafka 即可启动。</p>
 */
@Configuration
@ConditionalOnProperty(name = "enterprise.kb.profile.inference.enabled", havingValue = "true")
public class ProfileKafkaConfig {

    /** 活动事件 topic 名（与生产者/消费者共用同一配置）。 */
    @Value("${enterprise.kb.profile.inference.topic:kb.user.activity}")
    private String topic;

    /** 降级线程池核心线程数。 */
    @Value("${enterprise.kb.profile.inference.fallback.core-pool-size:1}")
    private int corePoolSize;
    /** 降级线程池最大线程数（推断含 LLM 调用，刻意小以限制并发）。 */
    @Value("${enterprise.kb.profile.inference.fallback.max-pool-size:2}")
    private int maxPoolSize;
    /** 降级线程池队列容量（满则拒绝、丢弃本次推断，提供背压）。 */
    @Value("${enterprise.kb.profile.inference.fallback.queue-capacity:200}")
    private int queueCapacity;

    /**
     * 声明用户活动事件 topic（按 userId 分区保证单用户内有序）。
     *
     * @return NewTopic 定义，由 KafkaAdmin 在启动时创建
     */
    @Bean
    public NewTopic userActivityTopic() {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    /**
     * Kafka 不可用时的降级执行器：有界线程池，满则 Abort（由调用方捕获记录丢弃），避免无界堆积。
     *
     * @return 降级线程池
     */
    @Bean
    public Executor profileInferenceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("profile-inference-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
