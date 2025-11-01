package com.microservice.utilities.cache.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.microservice.utilities.common.config.ApplicationProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * L1 (Local) cache configuration using Caffeine.
 */
@Configuration
public class L1CacheConfig {

    private static final Logger logger = LoggerFactory.getLogger(L1CacheConfig.class);

    @Bean("l1CacheManager")
    public CacheManager l1CacheManager(ApplicationProperties applicationProperties, MeterRegistry meterRegistry) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                .maximumSize(applicationProperties.getCache().getL1MaxSize())
                .expireAfterWrite(Duration.ofSeconds(applicationProperties.getCache().getL1TtlSeconds()))
                .expireAfterAccess(Duration.ofSeconds(applicationProperties.getCache().getL1TtlSeconds() / 2))
                .recordStats()
                .removalListener(createRemovalListener())
                .evictionListener((key, value, cause) -> {
                    logger.debug("L1 cache eviction: key={}, cause={}", key, cause);
                    // Record eviction metrics
                    meterRegistry.counter("cache.evictions", 
                            "cache", "l1", 
                            "cause", cause.toString()).increment();
                });

        cacheManager.setCaffeine(caffeine);
        cacheManager.setAllowNullValues(false);
        
        // Pre-create common caches
        cacheManager.setCacheNames(java.util.Set.of(
                "users", "products", "orders", "sessions", "metadata"
        ));

        logger.info("L1 Cache configured with max size: {}, TTL: {}s", 
                applicationProperties.getCache().getL1MaxSize(),
                applicationProperties.getCache().getL1TtlSeconds());

        return cacheManager;
    }

    @Bean
    public L1CacheMetrics l1CacheMetrics(MeterRegistry meterRegistry) {
        return new L1CacheMetrics(meterRegistry);
    }

    @Bean
    public CacheWarmer cacheWarmer() {
        return new CacheWarmer();
    }

    private RemovalListener<Object, Object> createRemovalListener() {
        return (key, value, cause) -> {
            logger.debug("L1 cache removal: key={}, cause={}", key, cause);
        };
    }

    /**
     * L1 Cache metrics collector
     */
    public static class L1CacheMetrics {
        private final MeterRegistry meterRegistry;

        public L1CacheMetrics(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        public void recordHit(String cacheName) {
            meterRegistry.counter("cache.requests", 
                    "cache", cacheName, 
                    "layer", "l1", 
                    "result", "hit").increment();
        }

        public void recordMiss(String cacheName) {
            meterRegistry.counter("cache.requests", 
                    "cache", cacheName, 
                    "layer", "l1", 
                    "result", "miss").increment();
        }

        public void recordLoad(String cacheName, long loadTimeNanos) {
            meterRegistry.timer("cache.load", 
                    "cache", cacheName, 
                    "layer", "l1")
                    .record(Duration.ofNanos(loadTimeNanos));
        }
    }

    /**
     * Cache warming utility
     */
    public static class CacheWarmer {
        private static final Logger logger = LoggerFactory.getLogger(CacheWarmer.class);

        public void warmCache(CacheManager cacheManager, String cacheName, 
                             java.util.Map<Object, Object> warmupData) {
            
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                logger.warn("Cache '{}' not found for warming", cacheName);
                return;
            }

            logger.info("Warming cache '{}' with {} entries", cacheName, warmupData.size());
            
            warmupData.forEach((key, value) -> {
                try {
                    cache.put(key, value);
                } catch (Exception e) {
                    logger.warn("Failed to warm cache entry: key={}, error={}", key, e.getMessage());
                }
            });

            logger.info("Cache '{}' warming completed", cacheName);
        }

        public void warmCacheAsync(CacheManager cacheManager, String cacheName, 
                                  java.util.Map<Object, Object> warmupData) {
            
            java.util.concurrent.CompletableFuture.runAsync(() -> 
                    warmCache(cacheManager, cacheName, warmupData))
                    .exceptionally(throwable -> {
                        logger.error("Async cache warming failed for cache: " + cacheName, throwable);
                        return null;
                    });
        }
    }
}