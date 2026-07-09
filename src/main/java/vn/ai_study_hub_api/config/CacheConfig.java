package vn.ai_study_hub_api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Enables Spring's cache abstraction ({@code @Cacheable} / {@code @CacheEvict}) backed by Redis.
 *
 * <p>Values are serialized as JSON via {@link GenericJackson2JsonRedisSerializer} (embeds type info so
 * cached DTOs round-trip without bespoke mappers). Each named cache carries its own TTL so that
 * slowly-changing reads (trending documents, public tags) stay cached longer than the default.
 *
 * <p>Cache names are referenced elsewhere through the {@code CACHE_*} constants to avoid string drift.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Popular-public-documents ranking. Expensive native join+group-by; changes slowly. */
    public static final String CACHE_TRENDING_DOCUMENTS = "trendingDocuments";

    /** All public tags (onboarding survey). Tiny set, rarely mutates. */
    public static final String CACHE_PUBLIC_TAGS = "publicTags";

    @Value("${app.cache.trending-documents-ttl-minutes:10}")
    private long trendingTtlMinutes;

    @Value("${app.cache.public-tags-ttl-minutes:30}")
    private long publicTagsTtlMinutes;

    @Value("${app.cache.default-ttl-minutes:5}")
    private long defaultTtlMinutes;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration jsonConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(
                        SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        Map<String, RedisCacheConfiguration> perCacheConfig = new HashMap<>();
        perCacheConfig.put(CACHE_TRENDING_DOCUMENTS, jsonConfig.entryTtl(Duration.ofMinutes(trendingTtlMinutes)));
        perCacheConfig.put(CACHE_PUBLIC_TAGS, jsonConfig.entryTtl(Duration.ofMinutes(publicTagsTtlMinutes)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(jsonConfig.entryTtl(Duration.ofMinutes(defaultTtlMinutes)))
                .withInitialCacheConfigurations(perCacheConfig)
                .build();
    }
}
