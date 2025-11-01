package com.microservice.utilities.data.jdbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Utility for efficient batch processing of database operations.
 * Provides methods for bulk inserts, updates, and deletes with error handling.
 */
@Component
public class BatchProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BatchProcessor.class);
    private static final int DEFAULT_BATCH_SIZE = 1000;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ExecutorService executorService;

    public BatchProcessor(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.executorService = Executors.newFixedThreadPool(4);
    }

    /**
     * Execute batch insert with custom batch size
     */
    @Transactional
    public <T> BatchResult batchInsert(String sql, List<T> items, 
                                      BatchParameterSetter<T> parameterSetter, 
                                      int batchSize) {
        if (items == null || items.isEmpty()) {
            return new BatchResult(0, 0, new ArrayList<>());
        }

        List<BatchError> errors = new ArrayList<>();
        int totalProcessed = 0;
        int totalSuccessful = 0;

        logger.info("Starting batch insert of {} items with batch size {}", items.size(), batchSize);

        for (int i = 0; i < items.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, items.size());
            List<T> batch = items.subList(i, endIndex);
            
            try {
                int[] results = jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int index) throws SQLException {
                        parameterSetter.setValues(ps, batch.get(index));
                    }

                    @Override
                    public int getBatchSize() {
                        return batch.size();
                    }
                });

                totalProcessed += batch.size();
                for (int result : results) {
                    if (result > 0) {
                        totalSuccessful++;
                    }
                }

                logger.debug("Processed batch {}-{}: {} items", i, endIndex - 1, batch.size());

            } catch (Exception e) {
                logger.error("Error processing batch {}-{}", i, endIndex - 1, e);
                errors.add(new BatchError(i, endIndex - 1, e.getMessage()));
                totalProcessed += batch.size();
            }
        }

        logger.info("Batch insert completed: {}/{} successful, {} errors", 
                   totalSuccessful, totalProcessed, errors.size());

        return new BatchResult(totalProcessed, totalSuccessful, errors);
    }

    /**
     * Execute batch insert with default batch size
     */
    @Transactional
    public <T> BatchResult batchInsert(String sql, List<T> items, BatchParameterSetter<T> parameterSetter) {
        return batchInsert(sql, items, parameterSetter, DEFAULT_BATCH_SIZE);
    }

    /**
     * Execute batch update using named parameters
     */
    @Transactional
    public BatchResult batchUpdate(String sql, List<Map<String, Object>> parameterMaps, int batchSize) {
        if (parameterMaps == null || parameterMaps.isEmpty()) {
            return new BatchResult(0, 0, new ArrayList<>());
        }

        List<BatchError> errors = new ArrayList<>();
        int totalProcessed = 0;
        int totalSuccessful = 0;

        logger.info("Starting batch update of {} items with batch size {}", parameterMaps.size(), batchSize);

        for (int i = 0; i < parameterMaps.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, parameterMaps.size());
            List<Map<String, Object>> batch = parameterMaps.subList(i, endIndex);
            
            try {
                int[] results = namedParameterJdbcTemplate.batchUpdate(sql, 
                    batch.toArray(new Map[0]));

                totalProcessed += batch.size();
                for (int result : results) {
                    if (result > 0) {
                        totalSuccessful++;
                    }
                }

                logger.debug("Processed batch {}-{}: {} items", i, endIndex - 1, batch.size());

            } catch (Exception e) {
                logger.error("Error processing batch {}-{}", i, endIndex - 1, e);
                errors.add(new BatchError(i, endIndex - 1, e.getMessage()));
                totalProcessed += batch.size();
            }
        }

        logger.info("Batch update completed: {}/{} successful, {} errors", 
                   totalSuccessful, totalProcessed, errors.size());

        return new BatchResult(totalProcessed, totalSuccessful, errors);
    }

    /**
     * Execute batch update with default batch size
     */
    @Transactional
    public BatchResult batchUpdate(String sql, List<Map<String, Object>> parameterMaps) {
        return batchUpdate(sql, parameterMaps, DEFAULT_BATCH_SIZE);
    }

    /**
     * Execute batch update using SqlParameterSource
     */
    @Transactional
    public BatchResult batchUpdate(String sql, List<SqlParameterSource> parameterSources, int batchSize) {
        if (parameterSources == null || parameterSources.isEmpty()) {
            return new BatchResult(0, 0, new ArrayList<>());
        }

        List<BatchError> errors = new ArrayList<>();
        int totalProcessed = 0;
        int totalSuccessful = 0;

        logger.info("Starting batch update of {} items with batch size {}", parameterSources.size(), batchSize);

        for (int i = 0; i < parameterSources.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, parameterSources.size());
            List<SqlParameterSource> batch = parameterSources.subList(i, endIndex);
            
            try {
                int[] results = namedParameterJdbcTemplate.batchUpdate(sql, 
                    batch.toArray(new SqlParameterSource[0]));

                totalProcessed += batch.size();
                for (int result : results) {
                    if (result > 0) {
                        totalSuccessful++;
                    }
                }

                logger.debug("Processed batch {}-{}: {} items", i, endIndex - 1, batch.size());

            } catch (Exception e) {
                logger.error("Error processing batch {}-{}", i, endIndex - 1, e);
                errors.add(new BatchError(i, endIndex - 1, e.getMessage()));
                totalProcessed += batch.size();
            }
        }

        logger.info("Batch update completed: {}/{} successful, {} errors", 
                   totalSuccessful, totalProcessed, errors.size());

        return new BatchResult(totalProcessed, totalSuccessful, errors);
    }

    /**
     * Execute batch delete
     */
    @Transactional
    public <T> BatchResult batchDelete(String sql, List<T> items, 
                                      BatchParameterSetter<T> parameterSetter, 
                                      int batchSize) {
        return batchInsert(sql, items, parameterSetter, batchSize); // Same logic
    }

    /**
     * Execute parallel batch processing
     */
    public <T> CompletableFuture<BatchResult> batchInsertAsync(String sql, List<T> items, 
                                                              BatchParameterSetter<T> parameterSetter, 
                                                              int batchSize) {
        return CompletableFuture.supplyAsync(() -> 
            batchInsert(sql, items, parameterSetter, batchSize), executorService);
    }

    /**
     * Execute batch with retry on failure
     */
    @Transactional
    public <T> BatchResult batchInsertWithRetry(String sql, List<T> items, 
                                               BatchParameterSetter<T> parameterSetter, 
                                               int batchSize, int maxRetries) {
        int attempts = 0;
        BatchResult lastResult = null;
        
        while (attempts < maxRetries) {
            try {
                lastResult = batchInsert(sql, items, parameterSetter, batchSize);
                if (lastResult.getErrors().isEmpty()) {
                    return lastResult;
                }
                attempts++;
                logger.warn("Batch insert attempt {} failed with {} errors, retrying...", 
                           attempts, lastResult.getErrors().size());
            } catch (Exception e) {
                attempts++;
                logger.error("Batch insert attempt {} failed", attempts, e);
                if (attempts >= maxRetries) {
                    throw new RuntimeException("Batch insert failed after " + maxRetries + " attempts", e);
                }
            }
        }
        
        return lastResult;
    }

    /**
     * Process large dataset in chunks with memory management
     */
    @Transactional
    public <T> BatchResult processLargeDataset(String sql, List<T> items, 
                                              BatchParameterSetter<T> parameterSetter,
                                              Function<List<T>, List<T>> preprocessor) {
        if (items == null || items.isEmpty()) {
            return new BatchResult(0, 0, new ArrayList<>());
        }

        List<BatchError> allErrors = new ArrayList<>();
        int totalProcessed = 0;
        int totalSuccessful = 0;
        int chunkSize = Math.min(DEFAULT_BATCH_SIZE * 10, 10000); // Process in larger chunks

        logger.info("Processing large dataset of {} items in chunks of {}", items.size(), chunkSize);

        for (int i = 0; i < items.size(); i += chunkSize) {
            int endIndex = Math.min(i + chunkSize, items.size());
            List<T> chunk = items.subList(i, endIndex);
            
            // Apply preprocessing if provided
            if (preprocessor != null) {
                chunk = preprocessor.apply(chunk);
            }
            
            BatchResult chunkResult = batchInsert(sql, chunk, parameterSetter, DEFAULT_BATCH_SIZE);
            
            totalProcessed += chunkResult.getTotalProcessed();
            totalSuccessful += chunkResult.getSuccessfulCount();
            allErrors.addAll(chunkResult.getErrors());
            
            // Force garbage collection for large datasets
            if (i % (chunkSize * 5) == 0) {
                System.gc();
            }
            
            logger.info("Processed chunk {}-{}: {}/{} successful", 
                       i, endIndex - 1, chunkResult.getSuccessfulCount(), chunkResult.getTotalProcessed());
        }

        logger.info("Large dataset processing completed: {}/{} successful, {} errors", 
                   totalSuccessful, totalProcessed, allErrors.size());

        return new BatchResult(totalProcessed, totalSuccessful, allErrors);
    }

    /**
     * Shutdown the executor service
     */
    public void shutdown() {
        executorService.shutdown();
    }

    /**
     * Interface for setting batch parameters
     */
    @FunctionalInterface
    public interface BatchParameterSetter<T> {
        void setValues(PreparedStatement ps, T item) throws SQLException;
    }

    /**
     * Batch processing result
     */
    public static class BatchResult {
        private final int totalProcessed;
        private final int successfulCount;
        private final List<BatchError> errors;

        public BatchResult(int totalProcessed, int successfulCount, List<BatchError> errors) {
            this.totalProcessed = totalProcessed;
            this.successfulCount = successfulCount;
            this.errors = errors;
        }

        public int getTotalProcessed() { return totalProcessed; }
        public int getSuccessfulCount() { return successfulCount; }
        public int getFailedCount() { return totalProcessed - successfulCount; }
        public List<BatchError> getErrors() { return errors; }
        public boolean hasErrors() { return !errors.isEmpty(); }
        public double getSuccessRate() { 
            return totalProcessed == 0 ? 0.0 : (double) successfulCount / totalProcessed; 
        }
    }

    /**
     * Batch error information
     */
    public static class BatchError {
        private final int startIndex;
        private final int endIndex;
        private final String errorMessage;

        public BatchError(int startIndex, int endIndex, String errorMessage) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.errorMessage = errorMessage;
        }

        public int getStartIndex() { return startIndex; }
        public int getEndIndex() { return endIndex; }
        public String getErrorMessage() { return errorMessage; }
    }
}