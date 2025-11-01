package com.microservice.utilities.cache.config;

import com.microservice.utilities.cache.manager.MultiLayerCacheManager;
import com.microservice.utilities.cache.resolver.CacheResolver;
import com.microservice.utilities.cache.resolver.DefaultCacheResolver;
import com.microservice.utilities.cache.strategy.CacheStrategy;
import com.microservice.utilities.cache.strategy.CacheAsideStrategy;
import com.microservice.utilities.common.config.ApplicationProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Auto-configuration for cache utilities.
 */
@Configuration
@EnableConfigurationProperties(ApplicationProperties.class)
@Import({
        L1CacheConfig.class,
        L2CacheConfig.class
})
public class CacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CacheResolver cacheResolver(ApplicationProperties applicationProperties) {
        return new DefaultCacheResolver(applicationProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheStrategy cacheStrategy() {
        return new CacheAsideStrategy();
    }

    @Bean
    @Primary
    public MultiLayerCacheManager multiLayerCacheManager(
            CacheManager l1CacheManager,
            CacheManager l2CacheManager,
            CacheResolver cacheResolver,
            CacheStrategy cacheStrategy,
            MeterRegistry meterRegistry) {
        
        return new MultiLayerCacheManager(
                l1CacheManager,
                l2CacheManager,
                cacheResolver,
                cacheStrategy,
                meterRegistry
        );
    }
}