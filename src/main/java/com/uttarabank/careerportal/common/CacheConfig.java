package com.uttarabank.careerportal.common;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

  @Bean
  CacheManager cacheManager() {
    CaffeineCacheManager manager = new CaffeineCacheManager();
    manager.registerCustomCache(
        "masterData",
        Caffeine.newBuilder().maximumSize(1_000).expireAfterWrite(Duration.ofMinutes(30)).build());
    manager.registerCustomCache(
        "publicJobs",
        Caffeine.newBuilder().maximumSize(10).expireAfterWrite(Duration.ofSeconds(10)).build());
    return manager;
  }
}
