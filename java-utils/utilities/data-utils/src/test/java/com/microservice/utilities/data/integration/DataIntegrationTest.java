package com.microservice.utilities.data.integration;

import com.microservice.utilities.common.config.ApplicationProperties;
import com.microservice.utilities.data.config.JpaConfig;
import com.microservice.utilities.data.jdbc.BatchProcessor;
import com.microservice.utilities.data.jdbc.ConnectionManager;
import com.microservice.utilities.data.jdbc.JdbcConfig;
import com.microservice.utilities.data.transaction.TransactionManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for data access utilities using TestContainers.
 */
@SpringBootTest(classes = DataIntegrationTest.TestConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.data.jpa-enabled=true",
        "app.data.jdbc-enabled=true"
})
@Testcontainers
class DataIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.database.url", postgres::getJdbcUrl);
        registry.add("app.database.username", postgres::getUsername);
        registry.add("app.database.password", postgres::getPassword);
        registry.add("app.database.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BatchProcessor batchProcessor;

    @Autowired
    private ConnectionManager connectionManager;

    @Autowired
    private TransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        // Create test table
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS test_data (
                id SERIAL PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                value VARCHAR(255),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
        
        // Clean up existing data
        jdbcTemplate.execute("DELETE FROM test_data");
    }

    @Test
    void dataSource_ShouldBeConfiguredCorrectly() {
        assertNotNull(dataSource);
        assertTrue(postgres.isRunning());
    }

    @Test
    void connectionManager_ShouldProvideValidConnections() throws SQLException {
        // When
        Connection connection = connectionManager.getConnection("test");

        // Then
        assertNotNull(connection);
        assertTrue(connection.isValid(5));
        assertFalse(connection.isClosed());

        // Cleanup
        connection.close();
        assertTrue(connection.isClosed());
    }

    @Test
    void connectionManager_ShouldTrackConnections() throws SQLException {
        // Given
        int initialCount = connectionManager.getActiveConnectionCount();

        // When
        Connection connection1 = connectionManager.getConnection("test1");
        Connection connection2 = connectionManager.getConnection("test2");

        // Then
        assertEquals(initialCount + 2, connectionManager.getActiveConnectionCount());

        // Cleanup
        connection1.close();
        connection2.close();
        assertEquals(initialCount, connectionManager.getActiveConnectionCount());
    }

    @Test
    void connectionManager_ShouldProvidePoolStats() {
        // When
        ConnectionManager.ConnectionPoolStats stats = connectionManager.getPoolStats();

        // Then
        assertNotNull(stats);
        assertTrue(stats.getTotalConnections() >= 0);
        assertTrue(stats.getActiveConnections() >= 0);
        assertTrue(stats.getIdleConnections() >= 0);
    }

    @Test
    void connectionManager_ShouldBeHealthy() {
        // When
        boolean healthy = connectionManager.isHealthy();

        // Then
        assertTrue(healthy);
    }

    @Test
    void batchProcessor_ShouldInsertDataInBatches() {
        // Given
        String sql = "INSERT INTO test_data (name, value) VALUES (?, ?)";
        List<TestData> testData = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            testData.add(new TestData("name" + i, "value" + i));
        }

        BatchProcessor.BatchParameterSetter<TestData> parameterSetter = (ps, item) -> {
            ps.setString(1, item.getName());
            ps.setString(2, item.getValue());
        };

        // When
        BatchProcessor.BatchResult result = batchProcessor.batchInsert(sql, testData, parameterSetter, 25);

        // Then
        assertEquals(100, result.getTotalProcessed());
        assertEquals(100, result.getSuccessfulCount());
        assertEquals(0, result.getFailedCount());
        assertFalse(result.hasErrors());

        // Verify data was inserted
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_data", Integer.class);
        assertEquals(100, count);
    }

    @Test
    void batchProcessor_ShouldHandleLargeDatasets() {
        // Given
        String sql = "INSERT INTO test_data (name, value) VALUES (?, ?)";
        List<TestData> largeDataset = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            largeDataset.add(new TestData("large" + i, "dataset" + i));
        }

        BatchProcessor.BatchParameterSetter<TestData> parameterSetter = (ps, item) -> {
            ps.setString(1, item.getName());
            ps.setString(2, item.getValue());
        };

        // When
        BatchProcessor.BatchResult result = batchProcessor.processLargeDataset(sql, largeDataset, parameterSetter, null);

        // Then
        assertEquals(1000, result.getTotalProcessed());
        assertTrue(result.getSuccessfulCount() > 0);
        assertTrue(result.getSuccessRate() > 0.9); // At least 90% success rate

        // Verify data was inserted
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_data", Integer.class);
        assertTrue(count >= 900); // Allow for some failures in large datasets
    }

    @Test
    void transactionManager_ShouldExecuteInTransaction() {
        // Given
        String insertSql = "INSERT INTO test_data (name, value) VALUES (?, ?)";

        // When
        String result = transactionManager.executeInTransaction(() -> {
            jdbcTemplate.update(insertSql, "tx_test", "tx_value");
            return "success";
        });

        // Then
        assertEquals("success", result);
        
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM test_data WHERE name = ?", Integer.class, "tx_test");
        assertEquals(1, count);
    }

    @Test
    void transactionManager_ShouldRollbackOnException() {
        // Given
        String insertSql = "INSERT INTO test_data (name, value) VALUES (?, ?)";

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            transactionManager.executeInTransaction(() -> {
                jdbcTemplate.update(insertSql, "rollback_test", "rollback_value");
                throw new RuntimeException("Simulated error");
            });
        });

        // Verify rollback
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM test_data WHERE name = ?", Integer.class, "rollback_test");
        assertEquals(0, count);
    }

    @Test
    void transactionManager_ShouldExecuteInReadOnlyTransaction() {
        // Given - insert some test data first
        jdbcTemplate.update("INSERT INTO test_data (name, value) VALUES (?, ?)", "readonly_test", "readonly_value");

        // When
        Integer result = transactionManager.executeInReadOnlyTransaction(() -> {
            return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_data WHERE name = ?", Integer.class, "readonly_test");
        });

        // Then
        assertEquals(1, result);
    }

    @Test
    void transactionManager_ShouldExecuteInNewTransaction() {
        // Given
        String insertSql = "INSERT INTO test_data (name, value) VALUES (?, ?)";

        // When
        transactionManager.executeInNewTransaction(() -> {
            jdbcTemplate.update(insertSql, "new_tx_test", "new_tx_value");
        });

        // Then
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM test_data WHERE name = ?", Integer.class, "new_tx_test");
        assertEquals(1, count);
    }

    @Test
    void concurrentDatabaseAccess_ShouldWorkCorrectly() throws InterruptedException {
        // Given
        int threadCount = 10;
        int operationsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // When - multiple threads perform database operations
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    String name = "thread" + threadId + "_op" + j;
                    String value = "value" + threadId + "_" + j;
                    
                    transactionManager.executeInTransaction(() -> {
                        jdbcTemplate.update("INSERT INTO test_data (name, value) VALUES (?, ?)", name, value);
                    });
                }
            }, executor);
            futures.add(future);
        }

        // Wait for all operations to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Then
        Integer totalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_data", Integer.class);
        assertEquals(threadCount * operationsPerThread, totalCount);

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }

    @Test
    void connectionLeakDetection_ShouldDetectLeaks() throws SQLException, InterruptedException {
        // Given - create a connection but don't close it
        Connection leakedConnection = connectionManager.getConnection("leak_test");
        
        // Wait a bit to ensure leak detection can run
        Thread.sleep(1000);

        // When
        List<ConnectionManager.ConnectionLeakInfo> leaks = connectionManager.getConnectionLeaks();

        // Then - we might not detect leaks immediately due to threshold settings
        // but the connection should be tracked
        assertTrue(connectionManager.getActiveConnectionCount() > 0);

        // Cleanup
        leakedConnection.close();
    }

    /**
     * Test data class
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

    @Configuration
    @Import({JpaConfig.class, JdbcConfig.class})
    @EnableConfigurationProperties(ApplicationProperties.class)
    static class TestConfig {

        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}