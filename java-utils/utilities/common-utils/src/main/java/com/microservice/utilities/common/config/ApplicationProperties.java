package com.microservice.utilities.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application configuration properties with validation.
 * Provides type-safe configuration management across microservices.
 */
@ConfigurationProperties(prefix = "app")
@Validated
public class ApplicationProperties {

    @Valid
    @NotNull
    private Security security = new Security();

    @Valid
    @NotNull
    private Cache cache = new Cache();

    @Valid
    @NotNull
    private Database database = new Database();

    @Valid
    @NotNull
    private Observability observability = new Observability();

    @Valid
    @NotNull
    private Integration integration = new Integration();

    @Valid
    @NotNull
    private Data data = new Data();

    // Getters and Setters
    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public Database getDatabase() {
        return database;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }

    public Observability getObservability() {
        return observability;
    }

    public void setObservability(Observability observability) {
        this.observability = observability;
    }

    public Integration getIntegration() {
        return integration;
    }

    public void setIntegration(Integration integration) {
        this.integration = integration;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

    // Nested configuration classes
    public static class Security {
        @NotBlank
        private String jwtSecret = "default-secret-change-in-production";

        @Min(300) // 5 minutes minimum
        @Max(86400) // 24 hours maximum
        private int jwtExpirationSeconds = 3600; // 1 hour default

        private boolean enableCors = true;
        private String[] allowedOrigins = {"http://localhost:3000", "http://localhost:8080"};

        // Getters and Setters
        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public int getJwtExpirationSeconds() {
            return jwtExpirationSeconds;
        }

        public void setJwtExpirationSeconds(int jwtExpirationSeconds) {
            this.jwtExpirationSeconds = jwtExpirationSeconds;
        }

        public boolean isEnableCors() {
            return enableCors;
        }

        public void setEnableCors(boolean enableCors) {
            this.enableCors = enableCors;
        }

        public String[] getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String[] allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Cache {
        @Min(1)
        @Max(10000)
        private int l1MaxSize = 1000;

        @Min(60) // 1 minute minimum
        @Max(86400) // 24 hours maximum
        private int l1TtlSeconds = 3600; // 1 hour default

        @NotBlank
        private String redisHost = "localhost";

        @Min(1)
        @Max(65535)
        private int redisPort = 6379;

        @Min(1)
        @Max(86400)
        private int redisTtlSeconds = 7200; // 2 hours default

        // Getters and Setters
        public int getL1MaxSize() {
            return l1MaxSize;
        }

        public void setL1MaxSize(int l1MaxSize) {
            this.l1MaxSize = l1MaxSize;
        }

        public int getL1TtlSeconds() {
            return l1TtlSeconds;
        }

        public void setL1TtlSeconds(int l1TtlSeconds) {
            this.l1TtlSeconds = l1TtlSeconds;
        }

        public String getRedisHost() {
            return redisHost;
        }

        public void setRedisHost(String redisHost) {
            this.redisHost = redisHost;
        }

        public int getRedisPort() {
            return redisPort;
        }

        public void setRedisPort(int redisPort) {
            this.redisPort = redisPort;
        }

        public int getRedisTtlSeconds() {
            return redisTtlSeconds;
        }

        public void setRedisTtlSeconds(int redisTtlSeconds) {
            this.redisTtlSeconds = redisTtlSeconds;
        }
    }

    public static class Database {
        @NotBlank
        private String url = "jdbc:postgresql://localhost:5432/microservice";
        
        @NotBlank
        private String username = "microservice";
        
        @NotBlank
        private String password = "password";
        
        @NotBlank
        private String driverClassName = "org.postgresql.Driver";
        
        @NotBlank
        private String poolName = "microservice";

        @Min(1)
        @Max(100)
        private int maxPoolSize = 20;
        
        @Min(1)
        @Max(50)
        private int minIdle = 5;

        @Min(1000)
        @Max(60000)
        private long connectionTimeout = 30000; // 30 seconds
        
        @Min(60000)
        @Max(3600000)
        private long idleTimeout = 600000; // 10 minutes
        
        @Min(1800000)
        @Max(7200000)
        private long maxLifetime = 1800000; // 30 minutes
        
        private long leakDetectionThreshold = 60000; // 1 minute

        private boolean showSql = false;
        private boolean formatSql = false;

        // Getters and Setters
        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public String getPoolName() {
            return poolName;
        }

        public void setPoolName(String poolName) {
            this.poolName = poolName;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getMinIdle() {
            return minIdle;
        }

        public void setMinIdle(int minIdle) {
            this.minIdle = minIdle;
        }

        public long getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public long getIdleTimeout() {
            return idleTimeout;
        }

        public void setIdleTimeout(long idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        public long getMaxLifetime() {
            return maxLifetime;
        }

        public void setMaxLifetime(long maxLifetime) {
            this.maxLifetime = maxLifetime;
        }

        public long getLeakDetectionThreshold() {
            return leakDetectionThreshold;
        }

        public void setLeakDetectionThreshold(long leakDetectionThreshold) {
            this.leakDetectionThreshold = leakDetectionThreshold;
        }

        public boolean isShowSql() {
            return showSql;
        }

        public void setShowSql(boolean showSql) {
            this.showSql = showSql;
        }

        public boolean isFormatSql() {
            return formatSql;
        }

        public void setFormatSql(boolean formatSql) {
            this.formatSql = formatSql;
        }
    }

    public static class Jpa {
        @NotBlank
        private String dialect = "org.hibernate.dialect.PostgreSQLDialect";
        
        @NotBlank
        private String ddlAuto = "validate";
        
        private boolean showSql = false;
        private boolean formatSql = false;
        
        @Min(10)
        @Max(1000)
        private int batchSize = 50;
        
        @Min(10)
        @Max(1000)
        private int fetchSize = 100;
        
        private boolean secondLevelCacheEnabled = false;
        private boolean generateStatistics = false;
        private boolean enversEnabled = false;

        // Getters and Setters
        public String getDialect() {
            return dialect;
        }

        public void setDialect(String dialect) {
            this.dialect = dialect;
        }

        public String getDdlAuto() {
            return ddlAuto;
        }

        public void setDdlAuto(String ddlAuto) {
            this.ddlAuto = ddlAuto;
        }

        public boolean isShowSql() {
            return showSql;
        }

        public void setShowSql(boolean showSql) {
            this.showSql = showSql;
        }

        public boolean isFormatSql() {
            return formatSql;
        }

        public void setFormatSql(boolean formatSql) {
            this.formatSql = formatSql;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getFetchSize() {
            return fetchSize;
        }

        public void setFetchSize(int fetchSize) {
            this.fetchSize = fetchSize;
        }

        public boolean isSecondLevelCacheEnabled() {
            return secondLevelCacheEnabled;
        }

        public void setSecondLevelCacheEnabled(boolean secondLevelCacheEnabled) {
            this.secondLevelCacheEnabled = secondLevelCacheEnabled;
        }

        public boolean isGenerateStatistics() {
            return generateStatistics;
        }

        public void setGenerateStatistics(boolean generateStatistics) {
            this.generateStatistics = generateStatistics;
        }

        public boolean isEnversEnabled() {
            return enversEnabled;
        }

        public void setEnversEnabled(boolean enversEnabled) {
            this.enversEnabled = enversEnabled;
        }
    }

    public static class Data {
        private Jpa jpa = new Jpa();
        private boolean jpaEnabled = true;
        private boolean jdbcEnabled = true;

        public Jpa getJpa() {
            return jpa;
        }

        public void setJpa(Jpa jpa) {
            this.jpa = jpa;
        }

        public boolean isJpaEnabled() {
            return jpaEnabled;
        }

        public void setJpaEnabled(boolean jpaEnabled) {
            this.jpaEnabled = jpaEnabled;
        }

        public boolean isJdbcEnabled() {
            return jdbcEnabled;
        }

        public void setJdbcEnabled(boolean jdbcEnabled) {
            this.jdbcEnabled = jdbcEnabled;
        }
    }

    public static class Observability {
        private boolean tracingEnabled = true;
        private boolean metricsEnabled = true;

        @NotBlank
        private String serviceName = "microservice";

        @NotBlank
        private String serviceVersion = "1.0.0";

        private String jaegerEndpoint = "http://localhost:14268/api/traces";
        private String zipkinEndpoint = "http://localhost:9411/api/v2/spans";

        // Getters and Setters
        public boolean isTracingEnabled() {
            return tracingEnabled;
        }

        public void setTracingEnabled(boolean tracingEnabled) {
            this.tracingEnabled = tracingEnabled;
        }

        public boolean isMetricsEnabled() {
            return metricsEnabled;
        }

        public void setMetricsEnabled(boolean metricsEnabled) {
            this.metricsEnabled = metricsEnabled;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getServiceVersion() {
            return serviceVersion;
        }

        public void setServiceVersion(String serviceVersion) {
            this.serviceVersion = serviceVersion;
        }

        public String getJaegerEndpoint() {
            return jaegerEndpoint;
        }

        public void setJaegerEndpoint(String jaegerEndpoint) {
            this.jaegerEndpoint = jaegerEndpoint;
        }

        public String getZipkinEndpoint() {
            return zipkinEndpoint;
        }

        public void setZipkinEndpoint(String zipkinEndpoint) {
            this.zipkinEndpoint = zipkinEndpoint;
        }
    }

    public static class Integration {
        @Min(1000)
        @Max(60000)
        private int httpTimeoutMs = 30000; // 30 seconds

        @Min(1)
        @Max(10)
        private int httpRetryAttempts = 3;

        @Min(100)
        @Max(10000)
        private int httpRetryDelayMs = 1000; // 1 second

        private String kafkaBootstrapServers = "localhost:9092";
        private String kafkaGroupId = "microservice-group";

        // Getters and Setters
        public int getHttpTimeoutMs() {
            return httpTimeoutMs;
        }

        public void setHttpTimeoutMs(int httpTimeoutMs) {
            this.httpTimeoutMs = httpTimeoutMs;
        }

        public int getHttpRetryAttempts() {
            return httpRetryAttempts;
        }

        public void setHttpRetryAttempts(int httpRetryAttempts) {
            this.httpRetryAttempts = httpRetryAttempts;
        }

        public int getHttpRetryDelayMs() {
            return httpRetryDelayMs;
        }

        public void setHttpRetryDelayMs(int httpRetryDelayMs) {
            this.httpRetryDelayMs = httpRetryDelayMs;
        }

        public String getKafkaBootstrapServers() {
            return kafkaBootstrapServers;
        }

        public void setKafkaBootstrapServers(String kafkaBootstrapServers) {
            this.kafkaBootstrapServers = kafkaBootstrapServers;
        }

        public String getKafkaGroupId() {
            return kafkaGroupId;
        }

        public void setKafkaGroupId(String kafkaGroupId) {
            this.kafkaGroupId = kafkaGroupId;
        }
    }
}