package com.microservice.utilities.cache.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microservice.utilities.common.config.ApplicationProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * L2 (Distributed) cache configuration using Redis.
 */
@Configuration
@ConditionalOnProperty(name = "app.cache.redis-host")
public class L2CacheConfig {

    private static final Logger logger = LoggerFactory.getLogger(L2CacheConfig.class);

    @Bean
    public RedisConnectionFactory redisConnectionFactory(ApplicationProperties applicationProperties) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(applicationProperties.getCache().getRedisHost());
        config.setPort(applicationProperties.getCache().getRedisPort());
        
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.setValidateConnection(true);
        
        logger.info("Redis connection configured: {}:{}", 
                applicationProperties.getCache().getRedisHost(),
                applicationProperties.getCache().getRedisPort());
        
        return factory;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values
        ObjectMapper objectMapper = createObjectMapper();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.setDefaultSerializer(jsonSerializer);
        template.afterPropertiesSet();
        
        return template;
    }

    @Bean("l2CacheManager")
    public CacheManager l2CacheManager(RedisConnectionFactory connectionFactory, 
                                      ApplicationProperties applicationProperties) {
        
        ObjectMapper objectMapper = createObjectMapper();
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(applicationProperties.getCache().getRedisTtlSeconds()))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();

        // Configure cache-specific TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Short-lived caches (5 minutes)
        cacheConfigurations.put("sessions", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("temp", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // Medium-lived caches (1 hour)
        cacheConfigurations.put("users", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigurations.put("products", defaultConfig.entryTtl(Duration.ofHours(1)));
        
        // Long-lived caches (24 hours)
        cacheConfigurations.put("metadata", defaultConfig.entryTtl(Duration.ofHours(24)));
        cacheConfigurations.put("config", defaultConfig.entryTtl(Duration.ofHours(24)));

        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();

        logger.info("L2 Cache (Redis) configured with default TTL: {}s", 
                applicationProperties.getCache().getRedisTtlSeconds());

        return cacheManager;
    }

    @Bean
    public L2CacheMetrics l2CacheMetrics(MeterRegistry meterRegistry, RedisTemplate<String, Object> redisTemplate) {
        return new L2CacheMetrics(meterRegistry, redisTemplate);
    }

    @Bean
    public RedisHealthIndicator redisHealthIndicator(RedisTemplate<String, Object> redisTemplate) {
        return new RedisHealthIndicator(redisTemplate);
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return objectMapper;
    }

    /**
     * L2 Cache metrics collector
     */
    public static class L2CacheMetrics {
        private final MeterRegistry meterRegistry;
        private final RedisTemplate<String, Object> redisTemplate;

        public L2CacheMetrics(MeterRegistry meterRegistry, RedisTemplate<String, Object> redisTemplate) {
            this.meterRegistry = meterRegistry;
            this.redisTemplate = redisTemplate;
        }

        public void recordHit(String cacheName) {
            meterRegistry.counter("cache.requests", 
                    "cache", cacheName, 
                    "layer", "l2", 
                    "result", "hit").increment();
        }

        public void recordMiss(String cacheName) {
            meterRegistry.counter("cache.requests", 
                    "cache", cacheName, 
                    "layer", "l2", 
                    "result", "miss").increment();
        }

        public void recordConnectionFailure() {
            meterRegistry.counter("cache.connection.failures", 
                    "layer", "l2").increment();
        }

        @org.springframework.scheduling.annotation.Scheduled(fixedRate = 60000) // Every minute
        public void collectRedisMetrics() {
            try {
                // Collect Redis info
                org.springframework.data.redis.connection.RedisConnection connection = 
                        redisTemplate.getConnectionFactory().getConnection();
                
                java.util.Properties info = connection.info();
                
                // Memory usage
                String usedMemory = info.getProperty("used_memory");
                if (usedMemory != null) {
                    meterRegistry.gauge("redis.memory.used", Double.parseDouble(usedMemory));
                }
                
                // Connected clients
                String connectedClients = info.getProperty("connected_clients");
                if (connectedClients != null) {
                    meterRegistry.gauge("redis.clients.connected", Double.parseDouble(connectedClients));
                }
                
                connection.close();
                
            } catch (Exception e) {
                logger.warn("Failed to collect Redis metrics: {}", e.getMessage());
                recordConnectionFailure();
            }
        }
    }

    /**
     * Redis health indicator
     */
    public static class RedisHealthIndicator {
        private static final Logger logger = LoggerFactory.getLogger(RedisHealthIndicator.class);
        
        private final RedisTemplate<String, Object> redisTemplate;

        public RedisHealthIndicator(RedisTemplate<String, Object> redisTemplate) {
            this.redisTemplate = redisTemplate;
        }

        public boolean isHealthy() {
            try {
                redisTemplate.opsForValue().set("health:check", "ping", Duration.ofSeconds(5));
                String response = (String) redisTemplate.opsForValue().get("health:check");
                redisTemplate.delete("health:check");
                return "ping".equals(response);
            } catch (Exception e) {
                logger.warn("Redis health check failed: {}", e.getMessage());
                return false;
            }
        }

        @org.springframework.scheduling.annotation.Scheduled(fixedRate = 30000) // Every 30 seconds
        public void performHealthCheck() {
            boolean healthy = isHealthy();
            logger.debug("Redis health check: {}", healthy ? "HEALTHY" : "UNHEALTHY");
        }
    }
}