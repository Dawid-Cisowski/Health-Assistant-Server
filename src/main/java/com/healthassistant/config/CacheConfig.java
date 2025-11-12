package com.healthassistant.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
class CacheConfig {

    public static final String NONCE_CACHE = "nonces";

    @Bean
    public CacheManager cacheManager(AppProperties appProperties) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(NONCE_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(appProperties.getNonce().getCacheTtlSeconds(), TimeUnit.SECONDS)
                .maximumSize(10000));
        return cacheManager;
    }
}

