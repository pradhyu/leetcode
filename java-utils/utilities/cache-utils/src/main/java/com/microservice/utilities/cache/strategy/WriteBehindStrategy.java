package com.microservice.utilities.cache.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.scheduling.annotation.Async;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Write-behind (write-back) strategy implementation.
 * Data is written to cache immediately and to data store asynchronously.
 */
public class WriteBehindStrategy implements CacheStrategy {

    private static final Logger logger = LoggerFactory.getLogger(WriteBehindStrategy.class);

    private final BlockingQueue<CacheOperation> writeQueue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);
    private final int batchSize;
    private final long flushIntervalMs;

    public WriteBehindStrategy() {
        this(100, 5000); // Default: batch size 100, flush every 5 seconds
    }

    public WriteBehindStrategy(int batchSize, long flushIntervalMs) {
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        startBatchProcessor();
    }

    @Override
    public void put(Object key, Object value, Cache l1Cache, Cache l2Cache) {
        try {
            // Write to L1 cache immediately
            if (l1Cache != null) {
                l1Cache.put(key, value);
                logger.debug("Write-behind: Put key {} in L1 cache immediately", key);
            }

            // Queue L2 write for asynchronous processing
            if (l2Cache != null) {
                CacheOperation operation = new CacheOperation(CacheOperationType.PUT, key, value, l2Cache);
                writeQueue.offer(operation);
                logger.debug("Write-behind: Queued L2 write for key {}", key);
            }
            
        } catch (Exception e) {
            logger.error("Write-behind put failed for key: {}", key, e);
        }
    }

    @Override
    public Cache.ValueWrapper get(Object key, Cache l1Cache, Cache l2Cache) {
        try {
            // Try L1 first (should be most up-to-date)
            if (l1Cache != null) {
                Cache.ValueWrapper l1Value = l1Cache.get(key);
                if (l1Value != null) {
                    logger.debug("Write-behind: L1 hit for key {}", key);
                    return l1Value;
                }
            }

            // Try L2 if L1 miss (might be stale due to async writes)
            if (l2Cache != null) {
                Cache.ValueWrapper l2Value = l2Cache.get(key);
                if (l2Value != null) {
                    logger.debug("Write-behind: L2 hit for key {}, promoting to L1", key);
                    // Promote to L1
                    if (l1Cache != null) {
                        l1Cache.put(key, l2Value.get());
                    }
                    return l2Value;
                }
            }

            logger.debug("Write-behind: Miss for key {}", key);
            return null;
            
        } catch (Exception e) {
            logger.error("Write-behind get failed for key: {}", key, e);
            return null;
        }
    }

    @Override
    public void evict(Object key, Cache l1Cache, Cache l2Cache) {
        try {
            // Evict from L1 immediately
            if (l1Cache != null) {
                l1Cache.evict(key);
                logger.debug("Write-behind: Evicted key {} from L1 cache immediately", key);
            }

            // Queue L2 eviction for asynchronous processing
            if (l2Cache != null) {
                CacheOperation operation = new CacheOperation(CacheOperationType.EVICT, key, null, l2Cache);
                writeQueue.offer(operation);
                logger.debug("Write-behind: Queued L2 eviction for key {}", key);
            }
            
        } catch (Exception e) {
            logger.error("Write-behind evict failed for key: {}", key, e);
        }
    }

    @Override
    public String getStrategyName() {
        return "write-behind";
    }

    private void startBatchProcessor() {
        // Batch processor that flushes writes periodically
        executorService.scheduleAtFixedRate(this::processBatch, 
                flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);

        // Size-based batch processor
        executorService.submit(this::processBatchBySize);
    }

    private void processBatch() {
        if (writeQueue.isEmpty()) {
            return;
        }

        logger.debug("Write-behind: Processing batch of {} operations", writeQueue.size());
        
        java.util.List<CacheOperation> batch = new java.util.ArrayList<>();
        writeQueue.drainTo(batch, batchSize);

        if (!batch.isEmpty()) {
            processBatchAsync(batch);
        }
    }

    private void processBatchBySize() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (writeQueue.size() >= batchSize) {
                    processBatch();
                }
                Thread.sleep(100); // Check every 100ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Async
    private void processBatchAsync(java.util.List<CacheOperation> batch) {
        CompletableFuture.runAsync(() -> {
            for (CacheOperation operation : batch) {
                try {
                    switch (operation.getType()) {
                        case PUT:
                            operation.getCache().put(operation.getKey(), operation.getValue());
                            logger.debug("Write-behind: Executed L2 put for key {}", operation.getKey());
                            break;
                        case EVICT:
                            operation.getCache().evict(operation.getKey());
                            logger.debug("Write-behind: Executed L2 evict for key {}", operation.getKey());
                            break;
                    }
                } catch (Exception e) {
                    logger.error("Write-behind: Failed to execute operation for key: {}", 
                            operation.getKey(), e);
                }
            }
        }).exceptionally(throwable -> {
            logger.error("Write-behind: Batch processing failed", throwable);
            return null;
        });
    }

    public void shutdown() {
        logger.info("Write-behind: Shutting down, processing remaining {} operations", writeQueue.size());
        
        // Process remaining operations
        processBatch();
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Cache operation for queuing
     */
    private static class CacheOperation {
        private final CacheOperationType type;
        private final Object key;
        private final Object value;
        private final Cache cache;

        public CacheOperation(CacheOperationType type, Object key, Object value, Cache cache) {
            this.type = type;
            this.key = key;
            this.value = value;
            this.cache = cache;
        }

        public CacheOperationType getType() { return type; }
        public Object getKey() { return key; }
        public Object getValue() { return value; }
        public Cache getCache() { return cache; }
    }

    private enum CacheOperationType {
        PUT, EVICT
    }
}