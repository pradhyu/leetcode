package com.microservice.utilities.cache.resolver;

import com.microservice.utilities.common.config.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default cache resolver implementation with access pattern tracking.
 */
@Component
public class DefaultCacheResolver implements CacheResolver {

    private static final Logger logger = LoggerFactory.getLogger(DefaultCacheResolver.class);

    private final ApplicationProperties applicationProperties;
    private final ConcurrentMap<Object, AccessPattern> accessPatterns = new ConcurrentHashMap<>();

    public DefaultCacheResolver(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @Override
    public CacheLayer resolveCache(Object key, CacheOperation operation) {
        AccessPattern pattern = accessPatterns.computeIfAbsent(key, k -> new AccessPattern());
        
        switch (operation) {
            case GET:
                pattern.incrementAccess();
                return determineLayerForGet(pattern);
            case PUT:
                pattern.incrementWrite();
                return CacheLayer.BOTH; // Always write to both layers
            case EVICT:
            case CLEAR:
                return CacheLayer.BOTH; // Always evict from both layers
            default:
                return CacheLayer.BOTH;
        }
    }

    @Override
    public boolean shouldCache(Object key, Object value) {
        if (key == null || value == null) {
            return false;
        }

        // Don't cache very large objects
        if (value instanceof String && ((String) value).length() > 10000) {
            return false;
        }

        // Cache based on access frequency
        AccessPattern pattern = accessPatterns.get(key);
        return pattern == null || pattern.getAccessCount() > 1;
    }

    @Override
    public long getCacheTtl(Object key) {
        AccessPattern pattern = accessPatterns.get(key);
        if (pattern == null) {
            return applicationProperties.getCache().getL1TtlSeconds();
        }

        // Frequently accessed items get longer TTL
        long accessCount = pattern.getAccessCount();
        if (accessCount > 100) {
            return applicationProperties.getCache().getL1TtlSeconds() * 2;
        } else if (accessCount > 10) {
            return applicationProperties.getCache().getL1TtlSeconds();
        } else {
            return applicationProperties.getCache().getL1TtlSeconds() / 2;
        }
    }

    private CacheLayer determineLayerForGet(AccessPattern pattern) {
        long accessCount = pattern.getAccessCount();
        
        // Frequently accessed items should be in L1
        if (accessCount > 10) {
            return CacheLayer.L1_ONLY;
        }
        
        // Less frequently accessed items can be in L2
        if (accessCount > 2) {
            return CacheLayer.BOTH;
        }
        
        // New items start in L2
        return CacheLayer.L2_ONLY;
    }

    /**
     * Tracks access patterns for cache keys
     */
    private static class AccessPattern {
        private final AtomicLong accessCount = new AtomicLong(0);
        private final AtomicLong writeCount = new AtomicLong(0);
        private volatile long lastAccessTime = System.currentTimeMillis();

        public void incrementAccess() {
            accessCount.incrementAndGet();
            lastAccessTime = System.currentTimeMillis();
        }

        public void incrementWrite() {
            writeCount.incrementAndGet();
            lastAccessTime = System.currentTimeMillis();
        }

        public long getAccessCount() {
            return accessCount.get();
        }

        public long getWriteCount() {
            return writeCount.get();
        }

        public long getLastAccessTime() {
            return lastAccessTime;
        }
    }
}