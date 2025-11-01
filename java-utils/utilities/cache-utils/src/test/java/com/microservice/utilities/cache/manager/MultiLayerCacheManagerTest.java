package com.microservice.utilities.cache.manager;

import com.microservice.utilities.cache.resolver.CacheResolver;
import com.microservice.utilities.cache.strategy.CacheStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiLayerCacheManagerTest {

    @Mock
    private CacheManager l1CacheManager;
    
    @Mock
    private CacheManager l2CacheManager;
    
    @Mock
    private CacheResolver cacheResolver;
    
    @Mock
    private CacheStrategy cacheStrategy;
    
    @Mock
    private Cache l1Cache;
    
    @Mock
    private Cache l2Cache;

    private MeterRegistry meterRegistry;
    private MultiLayerCacheManager multiLayerCacheManager;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        multiLayerCacheManager = new MultiLayerCacheManager(
                l1CacheManager, l2CacheManager, cacheResolver, cacheStrategy, meterRegistry);
    }

    @Test
    void getCache_ShouldCreateMultiLayerCache() {
        // Given
        String cacheName = "testCache";
        when(l1CacheManager.getCache(cacheName)).thenReturn(l1Cache);
        when(l2CacheManager.getCache(cacheName)).thenReturn(l2Cache);

        // When
        Cache cache = multiLayerCacheManager.getCache(cacheName);

        // Then
        assertNotNull(cache);
        assertEquals(cacheName, cache.getName());
        verify(l1CacheManager).getCache(cacheName);
        verify(l2CacheManager).getCache(cacheName);
    }

    @Test
    void getCache_ShouldReturnSameCacheInstance() {
        // Given
        String cacheName = "testCache";
        when(l1CacheManager.getCache(cacheName)).thenReturn(l1Cache);
        when(l2CacheManager.getCache(cacheName)).thenReturn(l2Cache);

        // When
        Cache cache1 = multiLayerCacheManager.getCache(cacheName);
        Cache cache2 = multiLayerCacheManager.getCache(cacheName);

        // Then
        assertSame(cache1, cache2);
        verify(l1CacheManager, times(1)).getCache(cacheName);
        verify(l2CacheManager, times(1)).getCache(cacheName);
    }

    @Test
    void getCacheNames_ShouldReturnCacheNames() {
        // Given
        String cacheName = "testCache";
        multiLayerCacheManager.getCache(cacheName);

        // When
        var cacheNames = multiLayerCacheManager.getCacheNames();

        // Then
        assertTrue(cacheNames.contains(cacheName));
    }

    @Test
    void multiLayerCache_Get_ShouldTryL1First() {
        // Given
        String cacheName = "testCache";
        Object key = "testKey";
        Object value = "testValue";
        
        when(l1CacheManager.getCache(cacheName)).thenReturn(l1Cache);
        when(l2CacheManager.getCache(cacheName)).thenReturn(l2Cache);
        when(l1Cache.get(key)).thenReturn(() -> value);

        Cache cache = multiLayerCacheManager.getCache(cacheName);

        // When
        Cache.ValueWrapper result = cache.get(key);

        // Then
        assertNotNull(result);
        assertEquals(value, result.get());
        verify(l1Cache).get(key);
        verify(l2Cache, never()).get(key);
    }

    @Test
    void multiLayerCache_Get_ShouldFallbackToL2OnL1Miss() {
        // Given
        String cacheName = "testCache";
        Object key = "testKey";
        Object value = "testValue";
        
        when(l1CacheManager.getCache(cacheName)).thenReturn(l1Cache);
        when(l2CacheManager.getCache(cacheName)).thenReturn(l2Cache);
        when(l1Cache.get(key)).thenReturn(null);
        when(l2Cache.get(key)).thenReturn(() -> value);

        Cache cache = multiLayerCacheManager.getCache(cacheName);

        // When
        Cache.ValueWrapper result = cache.get(key);

        // Then
        assertNotNull(result);
        assertEquals(value, result.get());
        verify(l1Cache).get(key);
        verify(l2Cache).get(key);
        verify(l1Cache).put(key, value); // Should promote to L1
    }

    @Test
    void multiLayerCache_Get_ShouldReturnNullOnBothMiss() {
        // Given
        String cacheName = "testCache";
        Object key = "testKey";
        
        when(l1CacheManager.getCache(cacheName)).thenReturn(l1Cache);
        when(l2CacheManager.getCache(cacheName)).thenReturn(l2Cache);
        when(l1Cache.get(key)).thenReturn(null);
        when(l2Cache.get(key)).thenReturn(null);

        Cache cache = multiLayerCacheManager.getCache(cacheName);

        // When
        Cache.ValueWrapper result = cache.get(key);

        // Then
        assertNull(result);
        verify(l1Cache).get(key);
        verify(l2Cache).get(key);
    }

    @Test
    void multiLayerCache_Put_ShouldUseCacheStrategy() {
        // Given
        String cacheName = "testCache";
        Object key = "testKey";
        Object value = "testValue";
        
        when(l1CacheManager.getCache(cacheName)).thenReturn(l1Cache);
        when(l2CacheManager.getCache(cacheName)).thenReturn(l2Cache);

        Cache cache = multiLayerCacheManager.getCache(cacheName);

        // When
        cache.put(key, value);

        // Then
        verify(cacheStrategy).put(key, value, l1Cache, l2Cache);
    }

    @Test
    void multiLayerCache_Evict_ShouldEvictFromBothLayers() {
        // Given
        String cacheName = "testCache";
        Object key = "testKey";
        
        when(l1CacheManager.getCache(cacheName)).thenReturn(l1Cache);
        when(l2CacheManager.getCache(cacheName)).thenReturn(l2Cache);

        Cache cache = multiLayerCacheManager.getCache(cacheName);

        // When
        cache.evict(key);

        // Then
        verify(l1Cache).evict(key);
        verify(l2Cache).evict(key);
    }

    @Test
    void multiLayerCache_Clear_ShouldClearBothLayers() {
        // Given
        String cacheName = "testCache";
        
        when(l1CacheManager.getCache(cacheName)).thenReturn(l1Cache);
        when(l2CacheManager.getCache(cacheName)).thenReturn(l2Cache);

        Cache cache = multiLayerCacheManager.getCache(cacheName);

        // When
        cache.clear();

        // Then
        verify(l1Cache).clear();
        verify(l2Cache).clear();
    }

    @Test
    void multiLayerCache_GetWithType_ShouldReturnTypedValue() {
        // Given
        String cacheName = "testCache";
        Object key = "testKey";
        String value = "testValue";
        
        when(l1CacheManager.getCache(cacheName)).thenReturn(l1Cache);
        when(l2CacheManager.getCache(cacheName)).thenReturn(l2Cache);
        when(l1Cache.get(key)).thenReturn(() -> value);

        Cache cache = multiLayerCacheManager.getCache(cacheName);

        // When
        String result = cache.get(key, String.class);

        // Then
        assertEquals(value, result);
    }

    @Test
    void multiLayerCache_GetWithValueLoader_ShouldLoadOnMiss() {
        // Given
        String cacheName = "testCache";
        Object key = "testKey";
        String value = "loadedValue";
        
        when(l1CacheManager.getCache(cacheName)).thenReturn(l1Cache);
        when(l2CacheManager.getCache(cacheName)).thenReturn(l2Cache);
        when(l1Cache.get(key)).thenReturn(null);
        when(l2Cache.get(key)).thenReturn(null);

        Cache cache = multiLayerCacheManager.getCache(cacheName);

        // When
        String result = cache.get(key, () -> value);

        // Then
        assertEquals(value, result);
        verify(cacheStrategy).put(key, value, l1Cache, l2Cache);
    }
}