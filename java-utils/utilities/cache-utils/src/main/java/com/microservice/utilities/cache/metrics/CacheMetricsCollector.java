package com.microservice.utilities.cache.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Collects and exposes cache metrics for monitoring.
 */
@Component
public class CacheMetricsCollector {

    private final CacheManager cacheManager;
    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, CacheStats> cacheStatsMap = new ConcurrentHashMap<>();

    public CacheMetricsCollector(CacheManager cacheManager, MeterRegistry meterRegistry) {
        this.cacheManager = cacheManager;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }

    private void initializeMetrics() {
        // Register gauges for cache statistics
        Gauge.builder("cache.size", this, CacheMetricsCollector::getTotalCacheSize)
                .description("Current cache size")
                .register(meterRegistry);

        Gauge.builder("cache.hit.ratio", this, CacheMetricsCollector::getOverallHitRatio)
                .description("Cache hit ratio")
                .register(meterRegistry);

        Gauge.builder("cache.eviction.count", this, CacheMetricsCollector::getTotalEvictions)
                .description("Total cache evictions")
                .register(meterRegistry);
    }

    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void collectMetrics() {
        for (String cacheName : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                collectCacheMetrics(cacheName, cache);
            }
        }
    }

    private void collectCacheMetrics(String cacheName, Cache cache) {
        CacheStats stats = cacheStatsMap.computeIfAbsent(cacheName, k -> new CacheStats());
        
        // Update cache-specific metrics
        Gauge.builder("cache.size", cache, this::getCacheSize)
                .tag("cache", cacheName)
                .description("Cache size for " + cacheName)
                .register(meterRegistry);

        Gauge.builder("cache.hit.ratio", stats, CacheStats::getHitRatio)
                .tag("cache", cacheName)
                .description("Hit ratio for " + cacheName)
                .register(meterRegistry);
    }

    public void recordCacheHit(String cacheName) {
        CacheStats stats = cacheStatsMap.computeIfAbsent(cacheName, k -> new CacheStats());
        stats.recordHit();
    }

    public void recordCacheMiss(String cacheName) {
        CacheStats stats = cacheStatsMap.computeIfAbsent(cacheName, k -> new CacheStats());
        stats.recordMiss();
    }

    public void recordEviction(String cacheName) {
        CacheStats stats = cacheStatsMap.computeIfAbsent(cacheName, k -> new CacheStats());
        stats.recordEviction();
    }

    private double getCacheSize(Cache cache) {
        try {
            Object nativeCache = cache.getNativeCache();
            if (nativeCache instanceof com.github.benmanes.caffeine.cache.Cache) {
                return ((com.github.benmanes.caffeine.cache.Cache<?, ?>) nativeCache).estimatedSize();
            }
            // Add other cache implementations as needed
            return 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double getTotalCacheSize() {
        return cacheManager.getCacheNames().stream()
                .mapToDouble(name -> {
                    Cache cache = cacheManager.getCache(name);
                    return cache != null ? getCacheSize(cache) : 0.0;
                })
                .sum();
    }

    private double getOverallHitRatio() {
        long totalHits = cacheStatsMap.values().stream()
                .mapToLong(CacheStats::getHits)
                .sum();
        long totalRequests = cacheStatsMap.values().stream()
                .mapToLong(stats -> stats.getHits() + stats.getMisses())
                .sum();
        
        return totalRequests > 0 ? (double) totalHits / totalRequests : 0.0;
    }

    private double getTotalEvictions() {
        return cacheStatsMap.values().stream()
                .mapToLong(CacheStats::getEvictions)
                .sum();
    }

    /**
     * Cache statistics holder
     */
    private static class CacheStats {
        private volatile long hits = 0;
        private volatile long misses = 0;
        private volatile long evictions = 0;

        public void recordHit() {
            hits++;
        }

        public void recordMiss() {
            misses++;
        }

        public void recordEviction() {
            evictions++;
        }

        public long getHits() {
            return hits;
        }

        public long getMisses() {
            return misses;
        }

        public long getEvictions() {
            return evictions;
        }

        public double getHitRatio() {
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        }
    }
}