package com.fhirtransformer.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis cache configuration for distributed caching.
 * 
 * <p>
 * Provides caching for:
 * <ul>
 * <li>Tenant configurations</li>
 * <li>Terminology lookups</li>
 * <li>Frequently accessed data</li>
 * </ul>
 * 
 * <p>
 * Performance benefits:
 * <ul>
 * <li>50-100x faster lookups for cached data</li>
 * <li>Reduced database load</li>
 * <li>Better scalability</li>
 * </ul>
 * 
 * @author FHIR Transformer Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Configuration
@EnableCaching
public class CacheConfig {

        /**
         * Configure Redis cache manager with JSON serialization.
         * 
         * <p>
         * Configuration:
         * <ul>
         * <li>TTL: 1 hour</li>
         * <li>Key serialization: String</li>
         * <li>Value serialization: JSON</li>
         * <li>Null values: Not cached</li>
         * <li>Transaction aware: Yes</li>
         * </ul>
         * 
         * @param factory Redis connection factory (auto-configured by Spring Boot)
         * @return Configured cache manager
         */
        @Bean
        public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
                RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofHours(1))
                                .serializeKeysWith(RedisSerializationContext.SerializationPair
                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair
                                                .fromSerializer(RedisSerializer.json()))
                                .disableCachingNullValues();

                return RedisCacheManager.builder(factory)
                                .cacheDefaults(config)
                                .transactionAware()
                                .build();
        }
}
