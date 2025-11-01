package com.microservice.utilities.cache.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

/**
 * Cache-aside (lazy loading) strategy implementation.
 * Application is responsible for loading data into cache on cache miss.
 */
public class CacheAsideStrategy implements CacheStrategy {

    private static final Logger logger = LoggerFactory.getLogger(CacheAsideStrategy.class);

    @Override
    public void put(Object key, Object value, Cache l1Cache, Cache l2Cache) {
        try {
            // Put in both caches
            if (l1Cache != null) {
                l1Cache.put(key, value);
                logger.debug("Cache-aside: Put key {} in L1 cache", key);
            }
            
            if (l2Cache != null) {
                l2Cache.put(key, value);
                logger.debug("Cache-aside: Put key {} in L2 cache", key);
            }
        } catch (Exception e) {
            logger.error("Cache-aside put failed for key: {}", key, e);
        }
    }

    @Override
    public Cache.ValueWrapper get(Object key, Cache l1Cache, Cache l2Cache) {
        try {
            // Try L1 first
            if (l1Cache != null) {
                Cache.ValueWrapper l1Value = l1Cache.get(key);
                if (l1Value != null) {
                    logger.debug("Cache-aside: L1 hit for key {}", key);
                    return l1Value;
                }
            }

            // Try L2 if L1 miss
            if (l2Cache != null) {
                Cache.ValueWrapper l2Value = l2Cache.get(key);
                if (l2Value != null) {
                    logger.debug("Cache-aside: L2 hit for key {}, promoting to L1", key);
                    // Promote to L1
                    if (l1Cache != null) {
                        l1Cache.put(key, l2Value.get());
                    }
                    return l2Value;
                }
            }

            logger.debug("Cache-aside: Miss for key {}", key);
            return null;
            
        } catch (Exception e) {
            logger.error("Cache-aside get failed for key: {}", key, e);
            return null;
        }
    }

    @Override
    public void evict(Object key, Cache l1Cache, Cache l2Cache) {
        try {
            if (l1Cache != null) {
                l1Cache.evict(key);
                logger.debug("Cache-aside: Evicted key {} from L1 cache", key);
            }
            
            if (l2Cache != null) {
                l2Cache.evict(key);
                logger.debug("Cache-aside: Evicted key {} from L2 cache", key);
            }
        } catch (Exception e) {
            logger.error("Cache-aside evict failed for key: {}", key, e);
        }
    }

    @Override
    public String getStrategyName() {
        return "cache-aside";
    }
}