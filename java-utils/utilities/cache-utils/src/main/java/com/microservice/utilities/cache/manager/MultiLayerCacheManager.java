package com.microservice.utilities.cache.manager;

import com.microservice.utilities.cache.resolver.CacheResolver;
import com.microservice.utilities.cache.strategy.CacheStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Multi-layer cache manager supporting L1 (local) and L2 (distributed) caching.
 * Provides cache-aside, write-through, and write-behind strategies.
 */
public class MultiLayerCacheManager implements CacheManager {

    private static final Logger logger = LoggerFactory.getLogger(MultiLayerCacheManager.class);

    private final CacheManager l1CacheManager;
    private final CacheManager l2CacheManager;
    private final CacheResolver cacheResolver;
    private final CacheStrategy cacheStrategy;
    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>();

    public MultiLayerCacheManager(CacheManager l1CacheManager, 
                                 CacheManager l2CacheManager,
                                 CacheResolver cacheResolver,
                                 CacheStrategy cacheStrategy,
                                 MeterRegistry meterRegistry) {
        this.l1CacheManager = l1CacheManager;
        this.l2CacheManager = l2CacheManager;
        this.cacheResolver = cacheResolver;
        this.cacheStrategy = cacheStrategy;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Cache getCache(String name) {
        return cacheMap.computeIfAbsent(name, this::createMultiLayerCache);
    }

    @Override
    public Collection<String> getCacheNames() {
        return cacheMap.keySet();
    }

    private Cache createMultiLayerCache(String name) {
        Cache l1Cache = l1CacheManager.getCache(name);
        Cache l2Cache = l2CacheManager.getCache(name);
        
        return new MultiLayerCache(name, l1Cache, l2Cache, cacheResolver, cacheStrategy, meterRegistry);
    }

    /**
     * Multi-layer cache implementation
     */
    public static class MultiLayerCache implements Cache {
        
        private static final Logger logger = LoggerFactory.getLogger(MultiLayerCache.class);
        
        private final String name;
        private final Cache l1Cache;
        private final Cache l2Cache;
        private final CacheResolver cacheResolver;
        private final CacheStrategy cacheStrategy;
        private final Timer l1Timer;
        private final Timer l2Timer;

        public MultiLayerCache(String name, 
                              Cache l1Cache, 
                              Cache l2Cache,
                              CacheResolver cacheResolver,
                              CacheStrategy cacheStrategy,
                              MeterRegistry meterRegistry) {
            this.name = name;
            this.l1Cache = l1Cache;
            this.l2Cache = l2Cache;
            this.cacheResolver = cacheResolver;
            this.cacheStrategy = cacheStrategy;
            this.l1Timer = Timer.builder("cache.access")
                    .tag("cache", name)
                    .tag("layer", "l1")
                    .register(meterRegistry);
            this.l2Timer = Timer.builder("cache.access")
                    .tag("cache", name)
                    .tag("layer", "l2")
                    .register(meterRegistry);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getNativeCache() {
            return this;
        }

        @Override
        public ValueWrapper get(Object key) {
            try {
            return l1Timer.recordCallable(() -> {
                // Try L1 cache first
                ValueWrapper l1Value = l1Cache.get(key);
                if (l1Value != null) {
                    logger.debug("Cache hit in L1 for key: {}", key);
                    return l1Value;
                }

                // Try L2 cache
                return l2Timer.recordCallable(() -> {
                    ValueWrapper l2Value = l2Cache.get(key);
                    if (l2Value != null) {
                        logger.debug("Cache hit in L2 for key: {}, promoting to L1", key);
                        // Promote to L1 cache
                        l1Cache.put(key, l2Value.get());
                        return l2Value;
                    }

                    logger.debug("Cache miss for key: {}", key);
                    return null;
                });
            });
        } catch (Exception e) {
            logger.error("Error accessing cache for key: {}", key, e);
            return null;
        }
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            ValueWrapper wrapper = get(key);
            return wrapper != null ? (T) wrapper.get() : null;
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            ValueWrapper existingValue = get(key);
            if (existingValue != null) {
                return (T) existingValue.get();
            }

            try {
                T value = valueLoader.call();
                put(key, value);
                return value;
            } catch (Exception e) {
                throw new ValueRetrievalException(key, valueLoader, e);
            }
        }

        @Override
        public void put(Object key, Object value) {
            cacheStrategy.put(key, value, l1Cache, l2Cache);
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            ValueWrapper existingValue = get(key);
            if (existingValue == null) {
                put(key, value);
                return null;
            }
            return existingValue;
        }

        @Override
        public void evict(Object key) {
            l1Cache.evict(key);
            l2Cache.evict(key);
            logger.debug("Evicted key from both cache layers: {}", key);
        }

        @Override
        public boolean evictIfPresent(Object key) {
            boolean l1Evicted = l1Cache.evictIfPresent(key);
            boolean l2Evicted = l2Cache.evictIfPresent(key);
            boolean evicted = l1Evicted || l2Evicted;
            
            if (evicted) {
                logger.debug("Evicted key from cache layers: {}", key);
            }
            
            return evicted;
        }

        @Override
        public void clear() {
            l1Cache.clear();
            l2Cache.clear();
            logger.debug("Cleared all cache layers for cache: {}", name);
        }

        @Override
        public boolean invalidate() {
            boolean l1Invalidated = l1Cache.invalidate();
            boolean l2Invalidated = l2Cache.invalidate();
            return l1Invalidated && l2Invalidated;
        }
    }
}