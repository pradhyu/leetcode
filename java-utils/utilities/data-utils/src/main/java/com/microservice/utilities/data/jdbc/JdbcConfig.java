package com.microservice.utilities.data.jdbc;

import com.microservice.utilities.common.config.ApplicationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * JDBC configuration with template and transaction management.
 */
@Configuration
@ConditionalOnProperty(name = "app.data.jdbc-enabled", havingValue = "true", matchIfMissing = true)
public class JdbcConfig {

    private final ApplicationProperties applicationProperties;

    public JdbcConfig(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    /**
     * Standard JDBC Template
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        
        // Configure fetch size for better performance
        jdbcTemplate.setFetchSize(applicationProperties.getData().getJpa().getFetchSize());
        
        // Set query timeout
        jdbcTemplate.setQueryTimeout(30); // 30 seconds
        
        return jdbcTemplate;
    }

    /**
     * Named Parameter JDBC Template
     */
    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    /**
     * JDBC Transaction Manager (separate from JPA if needed)
     */
    @Bean("jdbcTransactionManager")
    public PlatformTransactionManager jdbcTransactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * JDBC Transaction Template
     */
    @Bean
    public TransactionTemplate jdbcTransactionTemplate(PlatformTransactionManager jdbcTransactionManager) {
        return new TransactionTemplate(jdbcTransactionManager);
    }
}