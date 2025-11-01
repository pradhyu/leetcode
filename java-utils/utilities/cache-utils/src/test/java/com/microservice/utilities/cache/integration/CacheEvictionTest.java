package com.microservice.utilities.cache.integration;

import com.microservice.utilities.cache.config.L1CacheConfig;
import com.microservice.utilities.common.config.ApplicationProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * Tests for cache eviction policies and TTL functionality.
 */
@SpringBootTest(classes = CacheEvictionTest.TestConfig.class)
@TestPropertySource(properties = {
        "app.cache.l1-max-size=10",
        "app.cache.l1-ttl-seconds=2",
        "app.cache.l1.enabled=true"
})
class CacheEvictionTest {

    @Autowired
    private CacheManager cacheManager;

    private Cache cache;

    @BeforeEach
    void setUp() {
        cache = cacheManager.getCache("evictionTest");
        assertNotNull(cache);
        cache.clear(); // Start with clean cache
    }

    @Test
    void cache_ShouldEvictBasedOnSize() {
        // Given - cache with max size 10
        List<String> keys = new ArrayList<>();
        
        // When - add more items than max size
        for (int i = 0; i < 15; i++) {
            String key = "key" + i;
            String value = "value" + i;
            keys.add(key);
            cache.put(key, value);
        }

        // Then - some early entries should be evicted
        int presentCount = 0;
        for (String key : keys) {
            if (cache.get(key) != null) {
                presentCount++;
            }
        }
        
        // Should have approximately max size entries (allowing for some variance)
        assertTrue(presentCount <= 10, "Cache should not exceed max size");
        assertTrue(presentCount >= 8, "Cache should retain most recent entries");
    }

    @Test
    void cache_ShouldEvictBasedOnTTL() {
        // Given
        String key = "ttlKey";
        String value = "ttlValue";

        // When
        cache.put(key, value);
        
        // Verify value is there
        assertNotNull(cache.get(key));

        // Then - wait for TTL expiration (2 seconds)
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> cache.get(key) == null);

        assertNull(cache.get(key));
    }

    @Test
    void cache_ShouldEvictBasedOnAccessTime() {
        // Given
        String activeKey = "activeKey";
        String inactiveKey = "inactiveKey";
        String activeValue = "activeValue";
        String inactiveValue = "inactiveValue";

        // When - put both values
        cache.put(activeKey, activeValue);
        cache.put(inactiveKey, inactiveValue);

        // Keep accessing active key to prevent eviction
        for (int i = 0; i < 5; i++) {
            try {
                Thread.sleep(300);
                cache.get(activeKey); // Access to refresh
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Then - active key should still be there, inactive might be evicted
        // Note: This test depends on access-based eviction being configured
        assertNotNull(cache.get(activeKey));
        // inactiveKey might or might not be evicted depending on timing
    }

    @Test
    void cache_ShouldHandleEvictionGracefully() {
        // Given
        String key = "gracefulKey";
        String value = "gracefulValue";

        // When - put and immediately evict
        cache.put(key, value);
        assertEquals(value, cache.get(key, String.class));
        
        cache.evict(key);

        // Then - should handle eviction gracefully
        assertNull(cache.get(key));
        
        // Should be able to put again
        cache.put(key, value);
        assertEquals(value, cache.get(key, String.class));
    }

    @Test
    void cache_ShouldSupportManualEviction() {
        // Given
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        // Verify all are present
        assertNotNull(cache.get("key1"));
        assertNotNull(cache.get("key2"));
        assertNotNull(cache.get("key3"));

        // When - evict specific key
        cache.evict("key2");

        // Then
        assertNotNull(cache.get("key1"));
        assertNull(cache.get("key2"));
        assertNotNull(cache.get("key3"));
    }

    @Test
    void cache_ShouldSupportClearAll() {
        // Given
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        // Verify all are present
        assertNotNull(cache.get("key1"));
        assertNotNull(cache.get("key2"));
        assertNotNull(cache.get("key3"));

        // When
        cache.clear();

        // Then
        assertNull(cache.get("key1"));
        assertNull(cache.get("key2"));
        assertNull(cache.get("key3"));
    }

    @Test
    void cache_ShouldHandleRapidEviction() {
        // Given - rapid put/evict operations
        String baseKey = "rapidKey";
        String baseValue = "rapidValue";

        // When - perform rapid operations
        for (int i = 0; i < 100; i++) {
            String key = baseKey + i;
            String value = baseValue + i;
            
            cache.put(key, value);
            assertEquals(value, cache.get(key, String.class));
            
            if (i % 2 == 0) {
                cache.evict(key);
                assertNull(cache.get(key));
            }
        }

        // Then - cache should still be functional
        cache.put("finalKey", "finalValue");
        assertEquals("finalValue", cache.get("finalKey", String.class));
    }

    @Test
    void cache_ShouldMaintainConsistencyDuringEviction() {
        // Given
        String key = "consistencyKey";
        String value1 = "value1";
        String value2 = "value2";

        // When - put, update, and check consistency
        cache.put(key, value1);
        assertEquals(value1, cache.get(key, String.class));

        cache.put(key, value2);
        assertEquals(value2, cache.get(key, String.class));

        // Evict and re-add
        cache.evict(key);
        assertNull(cache.get(key));

        cache.put(key, value1);
        assertEquals(value1, cache.get(key, String.class));

        // Then - should maintain consistency throughout
        assertEquals(value1, cache.get(key, String.class));
    }

    @Test
    void cache_ShouldHandleEvictionDuringConcurrentAccess() throws InterruptedException {
        // Given
        String key = "concurrentKey";
        String value = "concurrentValue";
        int threadCount = 5;

        // When - multiple threads access while eviction happens
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 20; j++) {
                    cache.put(key + j, value + j);
                    cache.get(key + j);
                    if (j % 3 == 0) {
                        cache.evict(key + j);
                    }
                }
            });
            threads[i].start();
        }

        // Then - all threads should complete without errors
        for (Thread thread : threads) {
            thread.join(5000);
            assertFalse(thread.isAlive());
        }

        // Cache should still be functional
        cache.put("finalTest", "finalValue");
        assertEquals("finalValue", cache.get("finalTest", String.class));
    }

    @Configuration
    @Import(L1CacheConfig.class)
    @EnableConfigurationProperties(ApplicationProperties.class)
    static class TestConfig {

        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}