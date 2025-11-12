package com.healthassistant.infrastructure.web.security;

import com.healthassistant.application.authentication.port.DeviceSecretProvider;
import com.healthassistant.config.AppProperties;
import com.healthassistant.domain.event.DeviceId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
class DeviceSecretProviderAdapter implements DeviceSecretProvider {

    private final AppProperties appProperties;

    @Override
    public Optional<byte[]> getSecret(DeviceId deviceId) {
        byte[] secret = appProperties.getHmac().getDeviceSecrets().get(deviceId.value());
        return Optional.ofNullable(secret);
    }
}

