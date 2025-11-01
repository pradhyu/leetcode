package com.microservice.utilities.data.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Connection manager with leak detection and monitoring capabilities.
 */
@Component
public class ConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionManager.class);

    private final DataSource dataSource;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, ConnectionInfo> activeConnections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final long leakDetectionThreshold;

    public ConnectionManager(DataSource dataSource, MeterRegistry meterRegistry) {
        this.dataSource = dataSource;
        this.meterRegistry = meterRegistry;
        this.leakDetectionThreshold = getLeakDetectionThreshold();
        
        initializeMetrics();
        startLeakDetection();
        startHealthMonitoring();
    }

    /**
     * Get a connection with leak detection
     */
    public Connection getConnection() throws SQLException {
        return getConnection("default");
    }

    /**
     * Get a connection with custom identifier for tracking
     */
    public Connection getConnection(String identifier) throws SQLException {
        long startTime = System.currentTimeMillis();
        
        try {
            Connection connection = dataSource.getConnection();
            String connectionId = generateConnectionId(connection, identifier);
            
            // Track connection
            activeConnections.put(connectionId, new ConnectionInfo(
                connectionId, identifier, startTime, Thread.currentThread().getName(),
                new Exception("Connection allocation stack trace")
            ));
            
            logger.debug("Connection acquired: {} for identifier: {}", connectionId, identifier);
            
            return new TrackedConnection(connection, connectionId, this);
            
        } catch (SQLException e) {
            logger.error("Failed to acquire connection for identifier: {}", identifier, e);
            throw e;
        }
    }

    /**
     * Release connection tracking
     */
    void releaseConnection(String connectionId) {
        ConnectionInfo info = activeConnections.remove(connectionId);
        if (info != null) {
            long duration = System.currentTimeMillis() - info.getAcquisitionTime();
            logger.debug("Connection released: {} after {}ms", connectionId, duration);
            
            // Record connection usage metrics
            meterRegistry.timer("database.connection.usage")
                .record(duration, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Get connection pool statistics
     */
    public ConnectionPoolStats getPoolStats() {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDS = (HikariDataSource) dataSource;
            HikariPoolMXBean poolBean = hikariDS.getHikariPoolMXBean();
            
            return new ConnectionPoolStats(
                poolBean.getTotalConnections(),
                poolBean.getActiveConnections(),
                poolBean.getIdleConnections(),
                poolBean.getThreadsAwaitingConnection(),
                activeConnections.size()
            );
        }
        
        return new ConnectionPoolStats(0, 0, 0, 0, activeConnections.size());
    }

    /**
     * Check if connection pool is healthy
     */
    public boolean isHealthy() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(5); // 5 second timeout
        } catch (SQLException e) {
            logger.warn("Connection pool health check failed", e);
            return false;
        }
    }

    /**
     * Get active connection count
     */
    public int getActiveConnectionCount() {
        return activeConnections.size();
    }

    /**
     * Get connection leak information
     */
    public java.util.List<ConnectionLeakInfo> getConnectionLeaks() {
        long currentTime = System.currentTimeMillis();
        java.util.List<ConnectionLeakInfo> leaks = new java.util.ArrayList<>();
        
        for (ConnectionInfo info : activeConnections.values()) {
            long age = currentTime - info.getAcquisitionTime();
            if (age > leakDetectionThreshold) {
                leaks.add(new ConnectionLeakInfo(
                    info.getConnectionId(),
                    info.getIdentifier(),
                    age,
                    info.getThreadName(),
                    info.getAllocationStackTrace()
                ));
            }
        }
        
        return leaks;
    }

    /**
     * Force close leaked connections
     */
    public int closeLeakedConnections() {
        java.util.List<ConnectionLeakInfo> leaks = getConnectionLeaks();
        int closedCount = 0;
        
        for (ConnectionLeakInfo leak : leaks) {
            ConnectionInfo info = activeConnections.remove(leak.getConnectionId());
            if (info != null) {
                logger.warn("Force closing leaked connection: {} (age: {}ms, thread: {})", 
                           leak.getConnectionId(), leak.getAge(), leak.getThreadName());
                closedCount++;
            }
        }
        
        return closedCount;
    }

    /**
     * Initialize metrics
     */
    private void initializeMetrics() {
        // Active connections gauge
        Gauge.builder("database.connections.active")
            .description("Number of active database connections")
            .register(meterRegistry, this, ConnectionManager::getActiveConnectionCount);
        
        // Pool statistics if available
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDS = (HikariDataSource) dataSource;
            HikariPoolMXBean poolBean = hikariDS.getHikariPoolMXBean();
            
            Gauge.builder("database.connections.total")
                .description("Total number of connections in pool")
                .register(meterRegistry, poolBean, HikariPoolMXBean::getTotalConnections);
                
            Gauge.builder("database.connections.idle")
                .description("Number of idle connections in pool")
                .register(meterRegistry, poolBean, HikariPoolMXBean::getIdleConnections);
                
            Gauge.builder("database.connections.pending")
                .description("Number of threads waiting for connections")
                .register(meterRegistry, poolBean, HikariPoolMXBean::getThreadsAwaitingConnection);
        }
    }

    /**
     * Start leak detection monitoring
     */
    private void startLeakDetection() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                java.util.List<ConnectionLeakInfo> leaks = getConnectionLeaks();
                if (!leaks.isEmpty()) {
                    logger.warn("Detected {} connection leaks", leaks.size());
                    
                    for (ConnectionLeakInfo leak : leaks) {
                        logger.warn("Connection leak detected: {} (age: {}ms, thread: {})", 
                                   leak.getConnectionId(), leak.getAge(), leak.getThreadName());
                        
                        if (logger.isDebugEnabled()) {
                            logger.debug("Leak allocation stack trace:", leak.getAllocationStackTrace());
                        }
                    }
                    
                    // Record leak metrics
                    meterRegistry.counter("database.connections.leaks")
                        .increment(leaks.size());
                }
            } catch (Exception e) {
                logger.error("Error in leak detection", e);
            }
        }, 60, 60, TimeUnit.SECONDS); // Check every minute
    }

    /**
     * Start health monitoring
     */
    private void startHealthMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                boolean healthy = isHealthy();
                meterRegistry.gauge("database.health", healthy ? 1.0 : 0.0);
                
                if (!healthy) {
                    logger.warn("Database connection pool is unhealthy");
                }
            } catch (Exception e) {
                logger.error("Error in health monitoring", e);
            }
        }, 30, 30, TimeUnit.SECONDS); // Check every 30 seconds
    }

    /**
     * Get leak detection threshold
     */
    private long getLeakDetectionThreshold() {
        if (dataSource instanceof HikariDataSource) {
            return ((HikariDataSource) dataSource).getLeakDetectionThreshold();
        }
        return 60000; // Default 1 minute
    }

    /**
     * Generate unique connection ID
     */
    private String generateConnectionId(Connection connection, String identifier) {
        return identifier + "_" + connection.hashCode() + "_" + System.currentTimeMillis();
    }

    /**
     * Shutdown the connection manager
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Connection information for tracking
     */
    private static class ConnectionInfo {
        private final String connectionId;
        private final String identifier;
        private final long acquisitionTime;
        private final String threadName;
        private final Exception allocationStackTrace;

        public ConnectionInfo(String connectionId, String identifier, long acquisitionTime, 
                            String threadName, Exception allocationStackTrace) {
            this.connectionId = connectionId;
            this.identifier = identifier;
            this.acquisitionTime = acquisitionTime;
            this.threadName = threadName;
            this.allocationStackTrace = allocationStackTrace;
        }

        public String getConnectionId() { return connectionId; }
        public String getIdentifier() { return identifier; }
        public long getAcquisitionTime() { return acquisitionTime; }
        public String getThreadName() { return threadName; }
        public Exception getAllocationStackTrace() { return allocationStackTrace; }
    }

    /**
     * Connection pool statistics
     */
    public static class ConnectionPoolStats {
        private final int totalConnections;
        private final int activeConnections;
        private final int idleConnections;
        private final int pendingThreads;
        private final int trackedConnections;

        public ConnectionPoolStats(int totalConnections, int activeConnections, 
                                 int idleConnections, int pendingThreads, int trackedConnections) {
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.idleConnections = idleConnections;
            this.pendingThreads = pendingThreads;
            this.trackedConnections = trackedConnections;
        }

        public int getTotalConnections() { return totalConnections; }
        public int getActiveConnections() { return activeConnections; }
        public int getIdleConnections() { return idleConnections; }
        public int getPendingThreads() { return pendingThreads; }
        public int getTrackedConnections() { return trackedConnections; }
    }

    /**
     * Connection leak information
     */
    public static class ConnectionLeakInfo {
        private final String connectionId;
        private final String identifier;
        private final long age;
        private final String threadName;
        private final Exception allocationStackTrace;

        public ConnectionLeakInfo(String connectionId, String identifier, long age, 
                                String threadName, Exception allocationStackTrace) {
            this.connectionId = connectionId;
            this.identifier = identifier;
            this.age = age;
            this.threadName = threadName;
            this.allocationStackTrace = allocationStackTrace;
        }

        public String getConnectionId() { return connectionId; }
        public String getIdentifier() { return identifier; }
        public long getAge() { return age; }
        public String getThreadName() { return threadName; }
        public Exception getAllocationStackTrace() { return allocationStackTrace; }
    }
}