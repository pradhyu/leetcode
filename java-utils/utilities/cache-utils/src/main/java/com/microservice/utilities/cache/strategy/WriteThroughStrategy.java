package com.microservice.utilities.cache.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

import java.util.concurrent.CompletableFuture;

/**
 * Write-through strategy implementation.
 * Data is written to cache and data store synchronously.
 */
public class WriteThroughStrategy implements CacheStrategy {

    private static final Logger logger = LoggerFactory.getLogger(WriteThroughStrategy.class);

    @Override
    public void put(Object key, Object value, Cache l1Cache, Cache l2Cache) {
        try {
            // Write to both caches synchronously
            CompletableFuture<Void> l1Future = CompletableFuture.runAsync(() -> {
                if (l1Cache != null) {
                    l1Cache.put(key, value);
                    logger.debug("Write-through: Put key {} in L1 cache", key);
                }
            });

            CompletableFuture<Void> l2Future = CompletableFuture.runAsync(() -> {
                if (l2Cache != null) {
                    l2Cache.put(key, value);
                    logger.debug("Write-through: Put key {} in L2 cache", key);
                }
            });

            // Wait for both writes to complete
            CompletableFuture.allOf(l1Future, l2Future).join();
            
        } catch (Exception e) {
            logger.error("Write-through put failed for key: {}", key, e);
            throw new RuntimeException("Write-through cache put failed", e);
        }
    }

    @Override
    public Cache.ValueWrapper get(Object key, Cache l1Cache, Cache l2Cache) {
        try {
            // Try L1 first
            if (l1Cache != null) {
                Cache.ValueWrapper l1Value = l1Cache.get(key);
                if (l1Value != null) {
                    logger.debug("Write-through: L1 hit for key {}", key);
                    return l1Value;
                }
            }

            // Try L2 if L1 miss
            if (l2Cache != null) {
                Cache.ValueWrapper l2Value = l2Cache.get(key);
                if (l2Value != null) {
                    logger.debug("Write-through: L2 hit for key {}, promoting to L1", key);
                    // Promote to L1
                    if (l1Cache != null) {
                        l1Cache.put(key, l2Value.get());
                    }
                    return l2Value;
                }
            }

            logger.debug("Write-through: Miss for key {}", key);
            return null;
            
        } catch (Exception e) {
            logger.error("Write-through get failed for key: {}", key, e);
            return null;
        }
    }

    @Override
    public void evict(Object key, Cache l1Cache, Cache l2Cache) {
        try {
            // Evict from both caches synchronously
            CompletableFuture<Void> l1Future = CompletableFuture.runAsync(() -> {
                if (l1Cache != null) {
                    l1Cache.evict(key);
                    logger.debug("Write-through: Evicted key {} from L1 cache", key);
                }
            });

            CompletableFuture<Void> l2Future = CompletableFuture.runAsync(() -> {
                if (l2Cache != null) {
                    l2Cache.evict(key);
                    logger.debug("Write-through: Evicted key {} from L2 cache", key);
                }
            });

            // Wait for both evictions to complete
            CompletableFuture.allOf(l1Future, l2Future).join();
            
        } catch (Exception e) {
            logger.error("Write-through evict failed for key: {}", key, e);
        }
    }

    @Override
    public String getStrategyName() {
        return "write-through";
    }
}