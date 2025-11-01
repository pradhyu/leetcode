package com.microservice.utilities.cache.strategy;

import org.springframework.cache.Cache;

/**
 * Interface for different cache strategies.
 */
public interface CacheStrategy {

    /**
     * Put value into cache using the specific strategy
     */
    void put(Object key, Object value, Cache l1Cache, Cache l2Cache);

    /**
     * Get value from cache using the specific strategy
     */
    Cache.ValueWrapper get(Object key, Cache l1Cache, Cache l2Cache);

    /**
     * Evict value from cache using the specific strategy
     */
    void evict(Object key, Cache l1Cache, Cache l2Cache);

    /**
     * Get the strategy name
     */
    String getStrategyName();
}