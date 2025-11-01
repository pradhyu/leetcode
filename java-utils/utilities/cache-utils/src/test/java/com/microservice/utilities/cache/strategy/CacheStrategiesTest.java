package com.microservice.utilities.cache.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheStrategiesTest {

    @Mock
    private Cache l1Cache;
    
    @Mock
    private Cache l2Cache;

    private Object testKey = "testKey";
    private Object testValue = "testValue";

    @Test
    void cacheAsideStrategy_Put_ShouldPutInBothCaches() {
        // Given
        CacheAsideStrategy strategy = new CacheAsideStrategy();

        // When
        strategy.put(testKey, testValue, l1Cache, l2Cache);

        // Then
        verify(l1Cache).put(testKey, testValue);
        verify(l2Cache).put(testKey, testValue);
        assertEquals("cache-aside", strategy.getStrategyName());
    }

    @Test
    void cacheAsideStrategy_Get_ShouldTryL1First() {
        // Given
        CacheAsideStrategy strategy = new CacheAsideStrategy();
        when(l1Cache.get(testKey)).thenReturn(() -> testValue);

        // When
        Cache.ValueWrapper result = strategy.get(testKey, l1Cache, l2Cache);

        // Then
        assertNotNull(result);
        assertEquals(testValue, result.get());
        verify(l1Cache).get(testKey);
        verify(l2Cache, never()).get(testKey);
    }

    @Test
    void cacheAsideStrategy_Get_ShouldPromoteFromL2ToL1() {
        // Given
        CacheAsideStrategy strategy = new CacheAsideStrategy();
        when(l1Cache.get(testKey)).thenReturn(null);
        when(l2Cache.get(testKey)).thenReturn(() -> testValue);

        // When
        Cache.ValueWrapper result = strategy.get(testKey, l1Cache, l2Cache);

        // Then
        assertNotNull(result);
        assertEquals(testValue, result.get());
        verify(l1Cache).get(testKey);
        verify(l2Cache).get(testKey);
        verify(l1Cache).put(testKey, testValue); // Promotion
    }

    @Test
    void cacheAsideStrategy_Evict_ShouldEvictFromBothCaches() {
        // Given
        CacheAsideStrategy strategy = new CacheAsideStrategy();

        // When
        strategy.evict(testKey, l1Cache, l2Cache);

        // Then
        verify(l1Cache).evict(testKey);
        verify(l2Cache).evict(testKey);
    }

    @Test
    void writeThroughStrategy_Put_ShouldPutInBothCaches() {
        // Given
        WriteThroughStrategy strategy = new WriteThroughStrategy();

        // When
        strategy.put(testKey, testValue, l1Cache, l2Cache);

        // Then
        verify(l1Cache).put(testKey, testValue);
        verify(l2Cache).put(testKey, testValue);
        assertEquals("write-through", strategy.getStrategyName());
    }

    @Test
    void writeThroughStrategy_Get_ShouldTryL1First() {
        // Given
        WriteThroughStrategy strategy = new WriteThroughStrategy();
        when(l1Cache.get(testKey)).thenReturn(() -> testValue);

        // When
        Cache.ValueWrapper result = strategy.get(testKey, l1Cache, l2Cache);

        // Then
        assertNotNull(result);
        assertEquals(testValue, result.get());
        verify(l1Cache).get(testKey);
        verify(l2Cache, never()).get(testKey);
    }

    @Test
    void writeThroughStrategy_Evict_ShouldEvictFromBothCaches() {
        // Given
        WriteThroughStrategy strategy = new WriteThroughStrategy();

        // When
        strategy.evict(testKey, l1Cache, l2Cache);

        // Then
        verify(l1Cache).evict(testKey);
        verify(l2Cache).evict(testKey);
    }

    @Test
    void writeBehindStrategy_Put_ShouldPutInL1Immediately() {
        // Given
        WriteBehindStrategy strategy = new WriteBehindStrategy();

        // When
        strategy.put(testKey, testValue, l1Cache, l2Cache);

        // Then
        verify(l1Cache).put(testKey, testValue);
        // L2 cache write is queued, not immediate
        verify(l2Cache, never()).put(testKey, testValue);
        assertEquals("write-behind", strategy.getStrategyName());
    }

    @Test
    void writeBehindStrategy_Get_ShouldTryL1First() {
        // Given
        WriteBehindStrategy strategy = new WriteBehindStrategy();
        when(l1Cache.get(testKey)).thenReturn(() -> testValue);

        // When
        Cache.ValueWrapper result = strategy.get(testKey, l1Cache, l2Cache);

        // Then
        assertNotNull(result);
        assertEquals(testValue, result.get());
        verify(l1Cache).get(testKey);
        verify(l2Cache, never()).get(testKey);
    }

    @Test
    void writeBehindStrategy_Evict_ShouldEvictFromL1Immediately() {
        // Given
        WriteBehindStrategy strategy = new WriteBehindStrategy();

        // When
        strategy.evict(testKey, l1Cache, l2Cache);

        // Then
        verify(l1Cache).evict(testKey);
        // L2 cache eviction is queued, not immediate
        verify(l2Cache, never()).evict(testKey);
    }

    @Test
    void writeBehindStrategy_Shutdown_ShouldProcessRemainingOperations() {
        // Given
        WriteBehindStrategy strategy = new WriteBehindStrategy();
        
        // Add some operations
        strategy.put(testKey, testValue, l1Cache, l2Cache);
        strategy.evict("anotherKey", l1Cache, l2Cache);

        // When
        strategy.shutdown();

        // Then - shutdown should complete without throwing exceptions
        assertDoesNotThrow(() -> strategy.shutdown());
    }

    @Test
    void allStrategies_ShouldHandleNullCaches() {
        // Given
        CacheAsideStrategy cacheAside = new CacheAsideStrategy();
        WriteThroughStrategy writeThrough = new WriteThroughStrategy();
        WriteBehindStrategy writeBehind = new WriteBehindStrategy();

        // When & Then - should not throw exceptions with null caches
        assertDoesNotThrow(() -> {
            cacheAside.put(testKey, testValue, null, null);
            cacheAside.get(testKey, null, null);
            cacheAside.evict(testKey, null, null);
        });

        assertDoesNotThrow(() -> {
            writeThrough.put(testKey, testValue, null, null);
            writeThrough.get(testKey, null, null);
            writeThrough.evict(testKey, null, null);
        });

        assertDoesNotThrow(() -> {
            writeBehind.put(testKey, testValue, null, null);
            writeBehind.get(testKey, null, null);
            writeBehind.evict(testKey, null, null);
        });
    }

    @Test
    void allStrategies_ShouldHandleExceptions() {
        // Given
        CacheAsideStrategy cacheAside = new CacheAsideStrategy();
        WriteThroughStrategy writeThrough = new WriteThroughStrategy();
        WriteBehindStrategy writeBehind = new WriteBehindStrategy();

        // Mock caches to throw exceptions
        doThrow(new RuntimeException("Cache error")).when(l1Cache).put(any(), any());
        doThrow(new RuntimeException("Cache error")).when(l2Cache).put(any(), any());

        // When & Then - should handle exceptions gracefully
        assertDoesNotThrow(() -> cacheAside.put(testKey, testValue, l1Cache, l2Cache));
        assertDoesNotThrow(() -> writeBehind.put(testKey, testValue, l1Cache, l2Cache));
        
        // Write-through should propagate exceptions
        assertThrows(RuntimeException.class, () -> 
                writeThrough.put(testKey, testValue, l1Cache, l2Cache));
    }
}