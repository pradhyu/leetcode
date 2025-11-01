package com.microservice.utilities.cache.resolver;

import org.springframework.cache.Cache;

/**
 * Interface for resolving cache selection based on data access patterns.
 */
public interface CacheResolver {

    /**
     * Determine which cache layer to use for a given key and operation
     */
    CacheLayer resolveCache(Object key, CacheOperation operation);

    /**
     * Check if a key should be cached based on access patterns
     */
    boolean shouldCache(Object key, Object value);

    /**
     * Get cache TTL for a specific key
     */
    long getCacheTtl(Object key);

    /**
     * Cache layers enumeration
     */
    enum CacheLayer {
        L1_ONLY,
        L2_ONLY,
        BOTH,
        NONE
    }

    /**
     * Cache operations enumeration
     */
    enum CacheOperation {
        GET,
        PUT,
        EVICT,
        CLEAR
    }
}