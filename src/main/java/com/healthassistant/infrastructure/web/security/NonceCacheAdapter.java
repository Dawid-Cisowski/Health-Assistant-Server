package com.healthassistant.infrastructure.web.security;

import com.healthassistant.application.authentication.port.NonceCache;
import com.healthassistant.domain.event.DeviceId;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NonceCacheAdapter implements NonceCache {

    private static final String CACHE_NAME = "nonces";
    private final CacheManager cacheManager;

    @Override
    public boolean isUsed(DeviceId deviceId, String nonce) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            return false;
        }
        String cacheKey = deviceId.value() + ":" + nonce;
        return cache.get(cacheKey) != null;
    }

    @Override
    public void markAsUsed(DeviceId deviceId, String nonce) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            String cacheKey = deviceId.value() + ":" + nonce;
            cache.put(cacheKey, true);
        }
    }
}

