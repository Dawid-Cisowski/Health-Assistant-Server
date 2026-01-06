package com.healthassistant.security;

import com.healthassistant.security.api.NonceCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class NonceCacheAdapter implements NonceCache {

    private final CacheManager cacheManager;

    @Override
    public boolean markAsUsedIfAbsent(String deviceId, String nonce) {
        Cache cache = cacheManager.getCache(CacheConfig.NONCE_CACHE);
        if (cache == null) {
            log.error("SECURITY: Nonce cache unavailable, rejecting request");
            return false;
        }

        String key = deviceId + ":" + nonce;

        try {
            Cache.ValueWrapper existing = cache.putIfAbsent(key, Boolean.TRUE);
            return existing == null;
        } catch (Exception e) {
            log.error("SECURITY: Nonce cache operation failed, rejecting request", e);
            return false;
        }
    }
}
