package com.healthassistant.security.api;

public interface NonceCache {
    boolean isUsed(String deviceId, String nonce);
    void markAsUsed(String deviceId, String nonce);
}
