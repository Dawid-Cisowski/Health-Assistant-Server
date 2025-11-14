package com.healthassistant.application.authentication.port;

import java.util.Optional;

public interface DeviceSecretProvider {
    Optional<byte[]> getSecret(String deviceId);
}
