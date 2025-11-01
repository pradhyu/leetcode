package com.microservice.utilities.data.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for BatchProcessor functionality.
 */
@ExtendWith(MockitoExtension.class)
class BatchProcessorTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private PreparedStatement preparedStatement;

    private BatchProcessor batchProcessor;

    @BeforeEach
    void setUp() {
        batchProcessor = new BatchProcessor(jdbcTemplate, namedParameterJdbcTemplate);
    }

    @Test
    void batchInsert_WithValidData_ShouldReturnSuccessfulResult() throws SQLException {
        // Given
        String sql = "INSERT INTO test_table (name, value) VALUES (?, ?)";
        List<TestData> testData = Arrays.asList(
            new TestData("name1", "value1"),
            new TestData("name2", "value2"),
            new TestData("name3", "value3")
        );
        
        BatchProcessor.BatchParameterSetter<TestData> parameterSetter = (ps, item) -> {
            ps.setString(1, item.getName());
            ps.setString(2, item.getValue());
        };

        int[] expectedResults = {1, 1, 1}; // All successful
        when(jdbcTemplate.batchUpdate(eq(sql), any(BatchPreparedStatementSetter.class)))
            .thenReturn(expectedResults);

        // When
        BatchProcessor.BatchResult result = batchProcessor.batchInsert(sql, testData, parameterSetter, 10);

        // Then
        assertEquals(3, result.getTotalProcessed());
        assertEquals(3, result.getSuccessfulCount());
        assertEquals(0, result.getFailedCount());
        assertFalse(result.hasErrors());
        assertEquals(1.0, result.getSuccessRate());
        
        verify(jdbcTemplate).batchUpdate(eq(sql), any(BatchPreparedStatementSetter.class));
    }

    @Test
    void batchInsert_WithEmptyList_ShouldReturnEmptyResult() {
        // Given
        String sql = "INSERT INTO test_table (name, value) VALUES (?, ?)";
        List<TestData> emptyList = new ArrayList<>();
        BatchProcessor.BatchParameterSetter<TestData> parameterSetter = (ps, item) -> {};

        // When
        BatchProcessor.BatchResult result = batchProcessor.batchInsert(sql, emptyList, parameterSetter, 10);

        // Then
        assertEquals(0, result.getTotalProcessed());
        assertEquals(0, result.getSuccessfulCount());
        assertEquals(0, result.getFailedCount());
        assertFalse(result.hasErrors());
        
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void batchInsert_WithNullList_ShouldReturnEmptyResult() {
        // Given
        String sql = "INSERT INTO test_table (name, value) VALUES (?, ?)";
        BatchProcessor.BatchParameterSetter<TestData> parameterSetter = (ps, item) -> {};

        // When
        BatchProcessor.BatchResult result = batchProcessor.batchInsert(sql, null, parameterSetter, 10);

        // Then
        assertEquals(0, result.getTotalProcessed());
        assertEquals(0, result.getSuccessfulCount());
        assertEquals(0, result.getFailedCount());
        assertFalse(result.hasErrors());
        
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void batchInsert_WithBatchSizeLimit_ShouldProcessInBatches() throws SQLException {
        // Given
        String sql = "INSERT INTO test_table (name, value) VALUES (?, ?)";
        List<TestData> testData = Arrays.asList(
            new TestData("name1", "value1"),
            new TestData("name2", "value2"),
            new TestData("name3", "value3"),
            new TestData("name4", "value4"),
            new TestData("name5", "value5")
        );
        
        BatchProcessor.BatchParameterSetter<TestData> parameterSetter = (ps, item) -> {
            ps.setString(1, item.getName());
            ps.setString(2, item.getValue());
        };

        int batchSize = 2;
        int[] batch1Results = {1, 1}; // First batch: 2 items
        int[] batch2Results = {1, 1}; // Second batch: 2 items
        int[] batch3Results = {1};    // Third batch: 1 item
        
        when(jdbcTemplate.batchUpdate(eq(sql), any(BatchPreparedStatementSetter.class)))
            .thenReturn(batch1Results, batch2Results, batch3Results);

        // When
        BatchProcessor.BatchResult result = batchProcessor.batchInsert(sql, testData, parameterSetter, batchSize);

        // Then
        assertEquals(5, result.getTotalProcessed());
        assertEquals(5, result.getSuccessfulCount());
        assertEquals(0, result.getFailedCount());
        assertFalse(result.hasErrors());
        
        verify(jdbcTemplate, times(3)).batchUpdate(eq(sql), any(BatchPreparedStatementSetter.class));
    }

    @Test
    void batchInsert_WithException_ShouldRecordErrors() throws SQLException {
        // Given
        String sql = "INSERT INTO test_table (name, value) VALUES (?, ?)";
        List<TestData> testData = Arrays.asList(
            new TestData("name1", "value1"),
            new TestData("name2", "value2")
        );
        
        BatchProcessor.BatchParameterSetter<TestData> parameterSetter = (ps, item) -> {
            ps.setString(1, item.getName());
            ps.setString(2, item.getValue());
        };

        when(jdbcTemplate.batchUpdate(eq(sql), any(BatchPreparedStatementSetter.class)))
            .thenThrow(new RuntimeException("Database error"));

        // When
        BatchProcessor.BatchResult result = batchProcessor.batchInsert(sql, testData, parameterSetter, 10);

        // Then
        assertEquals(2, result.getTotalProcessed());
        assertEquals(0, result.getSuccessfulCount());
        assertEquals(2, result.getFailedCount());
        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrors().size());
        assertEquals("Database error", result.getErrors().get(0).getErrorMessage());
    }

    @Test
    void batchUpdate_WithParameterMaps_ShouldExecuteSuccessfully() {
        // Given
        String sql = "UPDATE test_table SET value = :value WHERE name = :name";
        List<Map<String, Object>> parameterMaps = Arrays.asList(
            createParameterMap("name1", "newValue1"),
            createParameterMap("name2", "newValue2")
        );

        int[] expectedResults = {1, 1};
        when(namedParameterJdbcTemplate.batchUpdate(eq(sql), any(Map[].class)))
            .thenReturn(expectedResults);

        // When
        BatchProcessor.BatchResult result = batchProcessor.batchUpdate(sql, parameterMaps, 10);

        // Then
        assertEquals(2, result.getTotalProcessed());
        assertEquals(2, result.getSuccessfulCount());
        assertEquals(0, result.getFailedCount());
        assertFalse(result.hasErrors());
        
        verify(namedParameterJdbcTemplate).batchUpdate(eq(sql), any(Map[].class));
    }

    @Test
    void batchUpdate_WithEmptyParameterMaps_ShouldReturnEmptyResult() {
        // Given
        String sql = "UPDATE test_table SET value = :value WHERE name = :name";
        List<Map<String, Object>> emptyMaps = new ArrayList<>();

        // When
        BatchProcessor.BatchResult result = batchProcessor.batchUpdate(sql, emptyMaps, 10);

        // Then
        assertEquals(0, result.getTotalProcessed());
        assertEquals(0, result.getSuccessfulCount());
        assertEquals(0, result.getFailedCount());
        assertFalse(result.hasErrors());
        
        verifyNoInteractions(namedParameterJdbcTemplate);
    }

    @Test
    void batchInsertWithRetry_ShouldRetryOnFailure() throws SQLException {
        // Given
        String sql = "INSERT INTO test_table (name, value) VALUES (?, ?)";
        List<TestData> testData = Arrays.asList(new TestData("name1", "value1"));
        BatchProcessor.BatchParameterSetter<TestData> parameterSetter = (ps, item) -> {
            ps.setString(1, item.getName());
            ps.setString(2, item.getValue());
        };

        // First call fails, second succeeds
        when(jdbcTemplate.batchUpdate(eq(sql), any(BatchPreparedStatementSetter.class)))
            .thenThrow(new RuntimeException("Temporary error"))
            .thenReturn(new int[]{1});

        // When
        BatchProcessor.BatchResult result = batchProcessor.batchInsertWithRetry(sql, testData, parameterSetter, 10, 2);

        // Then
        assertEquals(1, result.getTotalProcessed());
        assertEquals(1, result.getSuccessfulCount());
        assertEquals(0, result.getFailedCount());
        assertFalse(result.hasErrors());
        
        verify(jdbcTemplate, times(2)).batchUpdate(eq(sql), any(BatchPreparedStatementSetter.class));
    }

    @Test
    void processLargeDataset_ShouldHandleLargeDataEfficiently() throws SQLException {
        // Given
        String sql = "INSERT INTO test_table (name, value) VALUES (?, ?)";
        List<TestData> largeDataset = new ArrayList<>();
        for (int i = 0; i < 2500; i++) {
            largeDataset.add(new TestData("name" + i, "value" + i));
        }
        
        BatchProcessor.BatchParameterSetter<TestData> parameterSetter = (ps, item) -> {
            ps.setString(1, item.getName());
            ps.setString(2, item.getValue());
        };

        // Mock successful batch updates
        when(jdbcTemplate.batchUpdate(eq(sql), any(BatchPreparedStatementSetter.class)))
            .thenReturn(new int[1000]) // Return array of 1000 successful updates
            .thenReturn(new int[1000])
            .thenReturn(new int[500]);

        // When
        BatchProcessor.BatchResult result = batchProcessor.processLargeDataset(sql, largeDataset, parameterSetter, null);

        // Then
        assertEquals(2500, result.getTotalProcessed());
        assertTrue(result.getSuccessfulCount() > 0);
        assertFalse(result.hasErrors());
        
        // Should be called multiple times for large dataset
        verify(jdbcTemplate, atLeast(3)).batchUpdate(eq(sql), any(BatchPreparedStatementSetter.class));
    }

    @Test
    void batchResult_ShouldCalculateMetricsCorrectly() {
        // Given
        List<BatchProcessor.BatchError> errors = Arrays.asList(
            new BatchProcessor.BatchError(0, 9, "Error 1"),
            new BatchProcessor.BatchError(10, 19, "Error 2")
        );

        // When
        BatchProcessor.BatchResult result = new BatchProcessor.BatchResult(100, 80, errors);

        // Then
        assertEquals(100, result.getTotalProcessed());
        assertEquals(80, result.getSuccessfulCount());
        assertEquals(20, result.getFailedCount());
        assertEquals(0.8, result.getSuccessRate(), 0.001);
        assertTrue(result.hasErrors());
        assertEquals(2, result.getErrors().size());
    }

    @Test
    void batchError_ShouldStoreErrorInformation() {
        // Given
        int startIndex = 10;
        int endIndex = 19;
        String errorMessage = "Batch processing failed";

        // When
        BatchProcessor.BatchError error = new BatchProcessor.BatchError(startIndex, endIndex, errorMessage);

        // Then
        assertEquals(startIndex, error.getStartIndex());
        assertEquals(endIndex, error.getEndIndex());
        assertEquals(errorMessage, error.getErrorMessage());
    }

    private Map<String, Object> createParameterMap(String name, String value) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("value", value);
        return map;
    }

    /**
     * Test data class for batch processing tests
     */
    private static class TestData {
        private final String name;
        private final String value;

        public TestData(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() { return name; }
        public String getValue() { return value; }
    }
}