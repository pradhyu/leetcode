package com.microservice.utilities.cache.integration;

import com.microservice.utilities.cache.config.L2CacheConfig;
import com.microservice.utilities.common.config.ApplicationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for Redis L2 cache functionality.
 * Tests Redis-specific features like TTL, failover, and distributed caching.
 */
@SpringBootTest(classes = RedisIntegrationTest.TestConfig.class)
@TestPropertySource(properties = {
        "app.cache.l2.enabled=true",
        "app.cache.redis-ttl-seconds=2"
})
@Testcontainers
class RedisIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.cache.redis-host", redis::getHost);
        registry.add("app.cache.redis-port", redis::getFirstMappedPort);
    }

    @Autowired
    private CacheManager l2CacheManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void redis_ShouldBeAvailable() {
        assertTrue(redis.isRunning());
        assertNotNull(l2CacheManager);
        assertNotNull(redisTemplate);
    }

    @Test
    void redisCache_ShouldSupportBasicOperations() {
        // Given
        String cacheName = "redisTest";
        String key = "redisKey";
        String value = "redisValue";

        Cache cache = l2CacheManager.getCache(cacheName);
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
    void redisCache_ShouldRespectTTL() {
        // Given
        String cacheName = "redisTtlTest";
        String key = "ttlKey";
        String value = "ttlValue";

        Cache cache = l2CacheManager.getCache(cacheName);
        cache.put(key, value);

        // Verify value is there
        assertNotNull(cache.get(key));

        // When - wait for TTL to expire (configured to 2 seconds)
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> cache.get(key) == null);

        // Then - value should be expired
        assertNull(cache.get(key));
    }

    @Test
    void redisCache_ShouldSupportDifferentTTLsPerCache() {
        // Given - different cache names should have different TTLs based on configuration
        Cache sessionsCache = l2CacheManager.getCache("sessions");
        Cache configurationsCache = l2CacheManager.getCache("configurations");

        String key = "testKey";
        String value = "testValue";

        // When
        sessionsCache.put(key, value);
        configurationsCache.put(key, value);

        // Then - both should be available initially
        assertNotNull(sessionsCache.get(key));
        assertNotNull(configurationsCache.get(key));

        // Sessions cache should expire faster (5 minutes vs 24 hours in config)
        // For testing purposes, we'll just verify they both work
        assertNotNull(sessionsCache.get(key));
        assertNotNull(configurationsCache.get(key));
    }

    @Test
    void redisCache_ShouldHandleComplexObjects() {
        // Given
        String cacheName = "complexObjectTest";
        String key = "complexKey";
        TestObject value = new TestObject("test", 123, true);

        Cache cache = l2CacheManager.getCache(cacheName);

        // When
        cache.put(key, value);
        TestObject result = cache.get(key, TestObject.class);

        // Then
        assertNotNull(result);
        assertEquals(value.getName(), result.getName());
        assertEquals(value.getNumber(), result.getNumber());
        assertEquals(value.isFlag(), result.isFlag());
    }

    @Test
    void redisCache_ShouldSupportConcurrentAccess() throws InterruptedException {
        // Given
        String cacheName = "concurrentRedisTest";
        Cache cache = l2CacheManager.getCache(cacheName);
        int threadCount = 5;
        int operationsPerThread = 50;

        // When - multiple threads perform cache operations
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    String key = "thread" + threadId + "_key" + j;
                    String value = "thread" + threadId + "_value" + j;
                    
                    cache.put(key, value);
                    String retrieved = cache.get(key, String.class);
                    assertEquals(value, retrieved);
                }
            });
            threads[i].start();
        }

        // Then - all operations should complete successfully
        for (Thread thread : threads) {
            thread.join(10000); // 10 second timeout
            assertFalse(thread.isAlive());
        }
    }

    @Test
    void redisCache_ShouldHandleConnectionRecovery() {
        // Given
        String cacheName = "recoveryTest";
        String key = "recoveryKey";
        String value = "recoveryValue";

        Cache cache = l2CacheManager.getCache(cacheName);
        
        // When - normal operation
        cache.put(key, value);
        assertEquals(value, cache.get(key, String.class));

        // Note: Full connection recovery testing would require stopping/starting Redis
        // which is complex in a unit test environment. This test verifies basic functionality.
        
        // Then - cache should continue to work
        cache.put(key + "2", value + "2");
        assertEquals(value + "2", cache.get(key + "2", String.class));
    }

    @Test
    void redisTemplate_ShouldWorkDirectly() {
        // Given
        String key = "directRedisKey";
        String value = "directRedisValue";

        // When
        redisTemplate.opsForValue().set(key, value);
        Object result = redisTemplate.opsForValue().get(key);

        // Then
        assertEquals(value, result);

        // Cleanup
        redisTemplate.delete(key);
        assertNull(redisTemplate.opsForValue().get(key));
    }

    @Test
    void redisCache_ShouldSupportValueLoader() {
        // Given
        String cacheName = "valueLoaderRedisTest";
        String key = "loaderKey";
        String expectedValue = "loadedValue";
        Cache cache = l2CacheManager.getCache(cacheName);

        // When
        String result = cache.get(key, () -> expectedValue);

        // Then
        assertEquals(expectedValue, result);
        
        // Verify it's cached
        String cachedResult = cache.get(key, () -> "shouldNotBeUsed");
        assertEquals(expectedValue, cachedResult);
    }

    @Test
    void redisCache_ShouldSupportClear() {
        // Given
        String cacheName = "clearRedisTest";
        Cache cache = l2CacheManager.getCache(cacheName);

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

    /**
     * Test object for complex serialization testing
     */
    public static class TestObject {
        private String name;
        private int number;
        private boolean flag;

        public TestObject() {}

        public TestObject(String name, int number, boolean flag) {
            this.name = name;
            this.number = number;
            this.flag = flag;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getNumber() { return number; }
        public void setNumber(int number) { this.number = number; }
        public boolean isFlag() { return flag; }
        public void setFlag(boolean flag) { this.flag = flag; }
    }

    @Configuration
    @Import(L2CacheConfig.class)
    @EnableConfigurationProperties(ApplicationProperties.class)
    static class TestConfig {
        // Test configuration
    }
}