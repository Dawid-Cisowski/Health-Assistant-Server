package com.healthassistant.application.authentication.port;

import com.healthassistant.domain.event.DeviceId;

import java.util.Optional;

public interface DeviceSecretProvider {

    Optional<byte[]> getSecret(DeviceId deviceId);
}

