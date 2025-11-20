package com.healthassistant.security.api;

import java.util.Optional;

public interface DeviceSecretProvider {
    Optional<byte[]> getSecret(String deviceId);
}
