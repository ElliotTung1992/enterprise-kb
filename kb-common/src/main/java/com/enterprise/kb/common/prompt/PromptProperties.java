package com.enterprise.kb.common.prompt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Prompt 管理配置。
 */
@ConfigurationProperties(prefix = "enterprise.kb.prompt")
public class PromptProperties {

    /** 是否启用 LangFuse prompt 管理。 */
    private boolean enabled = false;
    /** LangFuse label。 */
    private String label = "production";
    /** LangFuse base URL。 */
    private String baseUrl = "http://langfuse-web:3000";
    /** LangFuse public key。 */
    private String publicKey = "";
    /** LangFuse secret key。 */
    private String secretKey = "";
    /** 缓存配置。 */
    private Cache cache = new Cache();
    /** 客户端配置。 */
    private Client client = new Client();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public static class Cache {
        private Duration ttl = Duration.ofSeconds(60);
        private Duration expire = Duration.ofHours(24);
        private long maximumSize = 100;

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public Duration getExpire() {
            return expire;
        }

        public void setExpire(Duration expire) {
            this.expire = expire;
        }

        public long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }
    }

    public static class Client {
        private Duration timeout = Duration.ofSeconds(3);
        private int maxRetries = 0;

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }
    }
}
