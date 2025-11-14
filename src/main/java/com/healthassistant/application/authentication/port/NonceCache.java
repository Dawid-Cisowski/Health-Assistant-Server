package com.healthassistant.application.authentication.port;

public interface NonceCache {
    boolean isUsed(String deviceId, String nonce);
    void markAsUsed(String deviceId, String nonce);
}
