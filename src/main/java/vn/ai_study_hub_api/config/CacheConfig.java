package vn.ai_study_hub_api.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    /** Admin AI/RAG dashboard payload (Langfuse Metrics API v2). Fan-out of ~15 queries → cache hard. */
    public static final String CACHE_AI_METRICS = "aiMetrics";

    private static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    @Value("${app.cache.trending-documents-ttl-minutes:10}")
    private long trendingTtlMinutes;

    @Value("${app.cache.public-tags-ttl-minutes:30}")
    private long publicTagsTtlMinutes;

    @Value("${app.cache.default-ttl-minutes:5}")
    private long defaultTtlMinutes;

    @Value("${app.cache.ai-metrics-ttl-minutes:5}")
    private long aiMetricsTtlMinutes;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        
        // Register JavaTimeModule to handle Java 8 date/time serialization (LocalDateTime)
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATETIME_FORMAT)));
        objectMapper.registerModule(javaTimeModule);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Enable default typing to embed class names so DTOs round-trip cleanly without custom mappers
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        RedisCacheConfiguration jsonConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(
                        SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)));

        Map<String, RedisCacheConfiguration> perCacheConfig = new HashMap<>();
        perCacheConfig.put(CACHE_TRENDING_DOCUMENTS, jsonConfig.entryTtl(Duration.ofMinutes(trendingTtlMinutes)));
        perCacheConfig.put(CACHE_PUBLIC_TAGS, jsonConfig.entryTtl(Duration.ofMinutes(publicTagsTtlMinutes)));
        perCacheConfig.put(CACHE_AI_METRICS, jsonConfig.entryTtl(Duration.ofMinutes(aiMetricsTtlMinutes)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(jsonConfig.entryTtl(Duration.ofMinutes(defaultTtlMinutes)))
                .withInitialCacheConfigurations(perCacheConfig)
                .build();
    }
}
