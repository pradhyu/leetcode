package com.microservice.utilities.cache.interceptor;

import com.microservice.utilities.cache.manager.MultiLayerCacheManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * AOP interceptor for method-level caching with metrics collection.
 */
@Aspect
@Component
public class CacheInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(CacheInterceptor.class);

    private final MultiLayerCacheManager cacheManager;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    public CacheInterceptor(MultiLayerCacheManager cacheManager, MeterRegistry meterRegistry) {
        this.cacheManager = cacheManager;
        this.cacheHitCounter = Counter.builder("cache.hits")
                .description("Number of cache hits")
                .register(meterRegistry);
        this.cacheMissCounter = Counter.builder("cache.misses")
                .description("Number of cache misses")
                .register(meterRegistry);
    }

    @Around("@annotation(cacheable)")
    public Object aroundCacheable(ProceedingJoinPoint joinPoint, Cacheable cacheable) throws Throwable {
        String cacheName = determineCacheName(cacheable);
        Object cacheKey = generateCacheKey(joinPoint, cacheable);

        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            logger.warn("Cache '{}' not found, proceeding without caching", cacheName);
            return joinPoint.proceed();
        }

        // Try to get from cache
        Cache.ValueWrapper cachedValue = cache.get(cacheKey);
        if (cachedValue != null) {
            logger.debug("Cache hit for key: {} in cache: {}", cacheKey, cacheName);
            cacheHitCounter.increment();
            return cachedValue.get();
        }

        // Cache miss - execute method and cache result
        logger.debug("Cache miss for key: {} in cache: {}", cacheKey, cacheName);
        cacheMissCounter.increment();
        
        Object result = joinPoint.proceed();
        
        if (result != null && shouldCacheResult(result, cacheable)) {
            cache.put(cacheKey, result);
            logger.debug("Cached result for key: {} in cache: {}", cacheKey, cacheName);
        }

        return result;
    }

    private String determineCacheName(Cacheable cacheable) {
        String[] cacheNames = cacheable.cacheNames();
        if (cacheNames.length > 0) {
            return cacheNames[0];
        }
        
        String[] value = cacheable.value();
        if (value.length > 0) {
            return value[0];
        }
        
        return "default";
    }

    private Object generateCacheKey(ProceedingJoinPoint joinPoint, Cacheable cacheable) {
        // Simple key generation - can be enhanced with SpEL evaluation
        Object[] args = joinPoint.getArgs();
        if (args.length == 0) {
            return joinPoint.getSignature().toShortString();
        }
        
        if (args.length == 1) {
            return args[0];
        }
        
        StringBuilder keyBuilder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                keyBuilder.append(":");
            }
            keyBuilder.append(args[i] != null ? args[i].toString() : "null");
        }
        
        return keyBuilder.toString();
    }

    private boolean shouldCacheResult(Object result, Cacheable cacheable) {
        // Don't cache null results unless explicitly configured
        if (result == null) {
            return cacheable.unless().isEmpty(); // Only cache null if no unless condition
        }
        
        return true;
    }
}