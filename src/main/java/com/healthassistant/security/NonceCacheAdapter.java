package com.healthassistant.security;

import com.healthassistant.security.api.NonceCache;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NonceCacheAdapter implements NonceCache {

    private final CacheManager cacheManager;

    @Override
    public boolean isUsed(String deviceId, String nonce) {
        Cache cache = cacheManager.getCache(CacheConfig.NONCE_CACHE);
        if (cache == null) {
            return false;
        }
        String key = deviceId + ":" + nonce;
        return cache.get(key) != null;
    }

    @Override
    public void markAsUsed(String deviceId, String nonce) {
        Cache cache = cacheManager.getCache(CacheConfig.NONCE_CACHE);
        if (cache != null) {
            String key = deviceId + ":" + nonce;
            cache.put(key, true);
        }
    }
}
