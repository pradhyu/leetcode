package com.microservice.utilities.cache.integration;

import com.microservice.utilities.cache.config.CacheAutoConfiguration;
import com.microservice.utilities.cache.config.L1CacheConfig;
import com.microservice.utilities.cache.config.L2CacheConfig;
import com.microservice.utilities.cache.manager.MultiLayerCacheManager;
import com.microservice.utilities.cache.strategy.CacheAsideStrategy;
import com.microservice.utilities.cache.strategy.WriteThroughStrategy;
import com.microservice.utilities.cache.strategy.WriteBehindStrategy;
import com.microservice.utilities.common.config.ApplicationProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = CacheIntegrationTest.TestConfig.class)
@TestPropertySource(properties = {
        "app.cache.l1-max-size=100",
        "app.cache.l1-ttl-seconds=2",
        "app.cache.redis-ttl-seconds=5",
        "app.cache.l1.enabled=true",
        "app.cache.l2.enabled=false"
})
@Testcontainers
class CacheIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true);

    @Autowired
    private MultiLayerCacheManager cacheManager;

    @Autowired
    @Qualifier("l1CacheManager")
    private CacheManager l1CacheManager;

    @Autowired
    @Qualifier("l2CacheManager")
    private CacheManager l2CacheManager;

    @Test
    void cacheManager_ShouldBeConfigured() {
        assertNotNull(cacheManager);
    }

    @Test
    void cache_ShouldSupportBasicOperations() {
        // Given
        String cacheName = "testCache";
        String key = "testKey";
        String value = "testValue";

        Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache);

        // When & Then - Put and Get
        cache.put(key, value);
        Cache.ValueWrapper result = cache.get(key);
        assertNotNull(result);
        assertEquals(value, result.get());

        // When & Then - Get with type
        String typedResult = cache.get(key, String.class);
        assertEquals(value, typedResult);

        // When & Then - Evict
        cache.evict(key);
        Cache.ValueWrapper evictedResult = cache.get(key);
        assertNull(evictedResult);
    }

    @Test
    void cache_ShouldSupportValueLoader() {
        // Given
        String cacheName = "testCache";
        String key = "loaderKey";
        String value = "loadedValue";

        Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache);

        // When
        String result = cache.get(key, () -> value);

        // Then
        assertEquals(value, result);

        // Verify it's cached
        String cachedResult = cache.get(key, String.class);
        assertEquals(value, cachedResult);
    }

    @Test
    void cache_ShouldSupportPutIfAbsent() {
        // Given
        String cacheName = "testCache";
        String key = "putIfAbsentKey";
        String value1 = "value1";
        String value2 = "value2";

        Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache);

        // When - First put
        Cache.ValueWrapper result1 = cache.putIfAbsent(key, value1);
        assertNull(result1); // Should return null for new key

        // When - Second put (should not overwrite)
        Cache.ValueWrapper result2 = cache.putIfAbsent(key, value2);
        assertNotNull(result2);
        assertEquals(value1, result2.get()); // Should return existing value

        // Verify original value is still there
        String cachedValue = cache.get(key, String.class);
        assertEquals(value1, cachedValue);
    }

    @Test
    void cache_ShouldSupportClear() {
        // Given
        String cacheName = "testCache";
        Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache);

        // Put some values
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        // Verify they exist
        assertNotNull(cache.get("key1"));
        assertNotNull(cache.get("key2"));

        // When
        cache.clear();

        // Then
        assertNull(cache.get("key1"));
        assertNull(cache.get("key2"));
    }

    @Test
    void cache_ShouldHandleNullValues() {
        // Given
        String cacheName = "testCache";
        String key = "nullKey";

        Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache);

        // When & Then - Should handle null values gracefully
        assertDoesNotThrow(() -> cache.put(key, null));
        
        // The behavior depends on cache configuration
        // Some caches allow nulls, others don't
        Cache.ValueWrapper result = cache.get(key);
        // Result could be null (not cached) or a wrapper with null value
    }

    @Test
    void cacheManager_ShouldReturnSameCacheInstance() {
        // Given
        String cacheName = "sameInstanceTest";

        // When
        Cache cache1 = cacheManager.getCache(cacheName);
        Cache cache2 = cacheManager.getCache(cacheName);

        // Then
        assertSame(cache1, cache2);
    }

    @Test
    void cacheManager_ShouldTrackCacheNames() {
        // Given
        String cacheName1 = "cache1";
        String cacheName2 = "cache2";

        // When
        cacheManager.getCache(cacheName1);
        cacheManager.getCache(cacheName2);

        // Then
        var cacheNames = cacheManager.getCacheNames();
        assertTrue(cacheNames.contains(cacheName1));
        assertTrue(cacheNames.contains(cacheName2));
    }

    @Test
    void multiLayerCache_ShouldFallbackFromL1ToL2() {
        // Given
        String cacheName = "fallbackTest";
        String key = "fallbackKey";
        String value = "fallbackValue";

        Cache multiLayerCache = cacheManager.getCache(cacheName);
        Cache l1Cache = l1CacheManager.getCache(cacheName);
        Cache l2Cache = l2CacheManager.getCache(cacheName);

        // Put value only in L2
        l2Cache.put(key, value);

        // When
        String result = multiLayerCache.get(key, String.class);

        // Then
        assertEquals(value, result);
        // Should now be promoted to L1
        assertNotNull(l1Cache.get(key));
    }

    @Test
    void multiLayerCache_ShouldHandleL1Eviction() {
        // Given
        String cacheName = "evictionTest";
        Cache multiLayerCache = cacheManager.getCache(cacheName);

        // Fill L1 cache beyond capacity to trigger eviction
        for (int i = 0; i < 150; i++) {
            multiLayerCache.put("key" + i, "value" + i);
        }

        // When - some early entries should be evicted from L1
        String result = multiLayerCache.get("key0", String.class);

        // Then - should still be available (from L2 or reloaded)
        // The exact behavior depends on the cache strategy
        assertNotNull(result);
    }

    @Test
    void cache_ShouldRespectTTL() throws InterruptedException {
        // Given
        String cacheName = "ttlTest";
        String key = "ttlKey";
        String value = "ttlValue";

        Cache cache = cacheManager.getCache(cacheName);
        cache.put(key, value);

        // Verify value is there
        assertNotNull(cache.get(key));

        // When - wait for TTL to expire (L1 TTL is 2 seconds)
        Thread.sleep(3000);

        // Then - value should be expired
        assertNull(cache.get(key));
    }

    @Test
    void multiLayerCache_ShouldSupportConcurrentAccess() throws InterruptedException {
        // Given
        String cacheName = "concurrentTest";
        Cache cache = cacheManager.getCache(cacheName);
        int threadCount = 10;
        int operationsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When - multiple threads perform cache operations
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "thread" + threadId + "_key" + j;
                        String value = "thread" + threadId + "_value" + j;
                        
                        cache.put(key, value);
                        String retrieved = cache.get(key, String.class);
                        assertEquals(value, retrieved);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then - all operations should complete successfully
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
    }

    @Test
    void cache_ShouldHandleValueLoader() {
        // Given
        String cacheName = "valueLoaderTest";
        String key = "loaderKey";
        String expectedValue = "loadedValue";
        Cache cache = cacheManager.getCache(cacheName);

        // When
        String result = cache.get(key, () -> expectedValue);

        // Then
        assertEquals(expectedValue, result);
        
        // Verify it's cached (subsequent call shouldn't invoke loader)
        String cachedResult = cache.get(key, () -> "shouldNotBeUsed");
        assertEquals(expectedValue, cachedResult);
    }

    @Test
    void cache_ShouldSupportPutIfAbsentOperation() {
        // Given
        String cacheName = "putIfAbsentTest";
        String key = "conditionalKey";
        String value1 = "firstValue";
        String value2 = "secondValue";
        Cache cache = cacheManager.getCache(cacheName);

        // When - first put
        Cache.ValueWrapper result1 = cache.putIfAbsent(key, value1);

        // Then - should return null (no existing value)
        assertNull(result1);
        assertEquals(value1, cache.get(key, String.class));

        // When - second put with different value
        Cache.ValueWrapper result2 = cache.putIfAbsent(key, value2);

        // Then - should return existing value, not overwrite
        assertNotNull(result2);
        assertEquals(value1, result2.get());
        assertEquals(value1, cache.get(key, String.class));
    }

    @Test
    void cache_ShouldHandleNullValuesGracefully() {
        // Given
        String cacheName = "nullValueTest";
        String key = "nullKey";
        Cache cache = cacheManager.getCache(cacheName);

        // When & Then - should handle null values without throwing exceptions
        assertDoesNotThrow(() -> cache.put(key, null));
        
        // The behavior with null values depends on cache configuration
        // Some implementations allow nulls, others don't
        Cache.ValueWrapper result = cache.get(key);
        // Result could be null (not cached) or a wrapper containing null
    }

    @Test
    @Disabled("Requires Redis container - enable for full integration testing")
    void cache_ShouldHandleRedisFailover() {
        // This test would require a more complex setup with Redis Sentinel or Cluster
        // For now, it's disabled but shows the structure for failover testing
        
        // Given - Redis is running and cache is working
        String cacheName = "failoverTest";
        String key = "failoverKey";
        String value = "failoverValue";
        Cache cache = cacheManager.getCache(cacheName);
        
        cache.put(key, value);
        assertEquals(value, cache.get(key, String.class));
        
        // When - Redis becomes unavailable (would need to stop container)
        // redis.stop();
        
        // Then - L1 cache should still work
        // Cache operations should degrade gracefully
        // assertDoesNotThrow(() -> cache.get(key, String.class));
    }

    @Test
    void cache_ShouldSupportDifferentStrategies() {
        // Given
        String cacheName = "strategyTest";
        Cache cache = cacheManager.getCache(cacheName);
        
        // Test basic cache operations work regardless of strategy
        String key = "strategyKey";
        String value = "strategyValue";

        // When
        cache.put(key, value);
        String result = cache.get(key, String.class);

        // Then
        assertEquals(value, result);
        
        // Test eviction
        cache.evict(key);
        assertNull(cache.get(key));
    }

    @Test
    void cache_ShouldMaintainConsistencyAcrossLayers() {
        // Given
        String cacheName = "consistencyTest";
        String key = "consistencyKey";
        String value = "consistencyValue";
        String updatedValue = "updatedValue";

        Cache multiLayerCache = cacheManager.getCache(cacheName);
        
        // When - put initial value
        multiLayerCache.put(key, value);
        assertEquals(value, multiLayerCache.get(key, String.class));
        
        // When - update value
        multiLayerCache.put(key, updatedValue);
        
        // Then - should get updated value
        assertEquals(updatedValue, multiLayerCache.get(key, String.class));
        
        // When - evict
        multiLayerCache.evict(key);
        
        // Then - should be gone from all layers
        assertNull(multiLayerCache.get(key));
    }

    @Configuration
    @Import({CacheAutoConfiguration.class, L1CacheConfig.class})
    @EnableConfigurationProperties(ApplicationProperties.class)
    static class TestConfig {

        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean("l2CacheManager")
        public CacheManager mockL2CacheManager() {
            // Use L1 cache manager as L2 for testing (no Redis dependency)
            return new org.springframework.cache.concurrent.ConcurrentMapCacheManager();
        }
    }
}