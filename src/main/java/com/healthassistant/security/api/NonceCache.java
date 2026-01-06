package com.healthassistant.security.api;

public interface NonceCache {

    boolean markAsUsedIfAbsent(String deviceId, String nonce);
}
