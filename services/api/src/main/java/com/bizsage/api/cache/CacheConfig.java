package com.bizsage.api.cache;

import java.time.Duration;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * V2: Multi-layer Redis cache configuration.
 *
 * <p>Four cache domains aligned with the V2 milestone targets:
 * <ul>
 *   <li><b>crawlerPages</b> — cached crawler page content (short TTL, high churn).</li>
 *   <li><b>apiResponses</b> — conversation list, user list (medium TTL).</li>
 *   <li><b>globalKnowledge</b> — static knowledge base (long TTL, low churn).</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

  /** Default TTL for uncategorized caches. */
  static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

  @Bean
  RedisCacheManagerBuilderCustomizer bizsageCacheCustomizer() {
    return builder -> builder
        .withCacheConfiguration("crawlerPages",
            RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15))
                .serializeValuesWith(SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer())))
        .withCacheConfiguration("apiResponses",
            RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeValuesWith(SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer())))
        .withCacheConfiguration("globalKnowledge",
            RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeValuesWith(SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer())));
  }
}
