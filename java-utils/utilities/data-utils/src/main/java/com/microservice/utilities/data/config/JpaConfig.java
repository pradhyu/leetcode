package com.microservice.utilities.data.config;

import com.microservice.utilities.common.config.ApplicationProperties;
import com.microservice.utilities.data.audit.AuditingConfig;
import com.microservice.utilities.data.repository.BaseRepositoryImpl;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * JPA configuration with HikariCP connection pooling and auditing.
 */
@Configuration
@EnableJpaRepositories(
    basePackages = "com.microservice",
    repositoryBaseClass = BaseRepositoryImpl.class
)
@EnableJpaAuditing
@EnableTransactionManagement
@EnableConfigurationProperties(ApplicationProperties.class)
@Import(AuditingConfig.class)
@ConditionalOnProperty(name = "app.data.jpa.enabled", havingValue = "true", matchIfMissing = true)
public class JpaConfig {

    private final ApplicationProperties applicationProperties;

    public JpaConfig(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    /**
     * HikariCP DataSource configuration optimized for containers
     */
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        
        ApplicationProperties.Database dbProps = applicationProperties.getDatabase();
        
        config.setJdbcUrl(dbProps.getUrl());
        config.setUsername(dbProps.getUsername());
        config.setPassword(dbProps.getPassword());
        config.setDriverClassName(dbProps.getDriverClassName());
        
        // Connection pool settings optimized for containers
        config.setMaximumPoolSize(dbProps.getMaxPoolSize());
        config.setMinimumIdle(dbProps.getMinIdle());
        config.setConnectionTimeout(dbProps.getConnectionTimeout());
        config.setIdleTimeout(dbProps.getIdleTimeout());
        config.setMaxLifetime(dbProps.getMaxLifetime());
        config.setLeakDetectionThreshold(dbProps.getLeakDetectionThreshold());
        
        // Performance optimizations
        config.setAutoCommit(false); // Let Spring manage transactions\n        config.setConnectionTestQuery(\"SELECT 1\");\n        config.setValidationTimeout(3000);\n        \n        // Connection pool name for monitoring\n        config.setPoolName(\"HikariPool-\" + dbProps.getPoolName());\n        \n        // Additional HikariCP properties\n        config.addDataSourceProperty(\"cachePrepStmts\", \"true\");\n        config.addDataSourceProperty(\"prepStmtCacheSize\", \"250\");\n        config.addDataSourceProperty(\"prepStmtCacheSqlLimit\", \"2048\");\n        config.addDataSourceProperty(\"useServerPrepStmts\", \"true\");\n        config.addDataSourceProperty(\"useLocalSessionState\", \"true\");\n        config.addDataSourceProperty(\"rewriteBatchedStatements\", \"true\");\n        config.addDataSourceProperty(\"cacheResultSetMetadata\", \"true\");\n        config.addDataSourceProperty(\"cacheServerConfiguration\", \"true\");\n        config.addDataSourceProperty(\"elideSetAutoCommits\", \"true\");\n        config.addDataSourceProperty(\"maintainTimeStats\", \"false\");\n        \n        return new HikariDataSource(config);\n    }\n\n    /**\n     * JPA EntityManagerFactory configuration\n     */\n    @Bean\n    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {\n        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();\n        em.setDataSource(dataSource);\n        em.setPackagesToScan(\"com.microservice\");\n        \n        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();\n        em.setJpaVendorAdapter(vendorAdapter);\n        em.setJpaProperties(hibernateProperties());\n        \n        return em;\n    }\n\n    /**\n     * JPA Transaction Manager\n     */\n    @Bean\n    public PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean entityManagerFactory) {\n        JpaTransactionManager transactionManager = new JpaTransactionManager();\n        transactionManager.setEntityManagerFactory(entityManagerFactory.getObject());\n        return transactionManager;\n    }\n\n    /**\n     * Hibernate properties configuration\n     */\n    private Properties hibernateProperties() {\n        Properties properties = new Properties();\n        ApplicationProperties.Jpa jpaProps = applicationProperties.getJpa();\n        \n        properties.setProperty(\"hibernate.dialect\", jpaProps.getDialect());\n        properties.setProperty(\"hibernate.hbm2ddl.auto\", jpaProps.getDdlAuto());\n        properties.setProperty(\"hibernate.show_sql\", String.valueOf(jpaProps.isShowSql()));\n        properties.setProperty(\"hibernate.format_sql\", String.valueOf(jpaProps.isFormatSql()));\n        \n        // Performance optimizations\n        properties.setProperty(\"hibernate.jdbc.batch_size\", String.valueOf(jpaProps.getBatchSize()));\n        properties.setProperty(\"hibernate.jdbc.fetch_size\", String.valueOf(jpaProps.getFetchSize()));\n        properties.setProperty(\"hibernate.order_inserts\", \"true\");\n        properties.setProperty(\"hibernate.order_updates\", \"true\");\n        properties.setProperty(\"hibernate.batch_versioned_data\", \"true\");\n        \n        // Second level cache\n        if (jpaProps.isSecondLevelCacheEnabled()) {\n            properties.setProperty(\"hibernate.cache.use_second_level_cache\", \"true\");\n            properties.setProperty(\"hibernate.cache.use_query_cache\", \"true\");\n            properties.setProperty(\"hibernate.cache.region.factory_class\", \n                \"org.hibernate.cache.jcache.JCacheRegionFactory\");\n        }\n        \n        // Statistics and monitoring\n        properties.setProperty(\"hibernate.generate_statistics\", String.valueOf(jpaProps.isGenerateStatistics()));\n        \n        // Connection handling\n        properties.setProperty(\"hibernate.connection.provider_disables_autocommit\", \"true\");\n        \n        // Envers (auditing) configuration\n        if (jpaProps.isEnversEnabled()) {\n            properties.setProperty(\"org.hibernate.envers.audit_table_suffix\", \"_AUD\");\n            properties.setProperty(\"org.hibernate.envers.revision_field_name\", \"REV\");\n            properties.setProperty(\"org.hibernate.envers.revision_type_field_name\", \"REVTYPE\");\n            properties.setProperty(\"org.hibernate.envers.store_data_at_delete\", \"true\");\n        }\n        \n        return properties;\n    }\n}"