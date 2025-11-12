package com.healthassistant.application.authentication.port;

import com.healthassistant.domain.event.DeviceId;

public interface NonceCache {

    boolean isUsed(DeviceId deviceId, String nonce);

    void markAsUsed(DeviceId deviceId, String nonce);
}

