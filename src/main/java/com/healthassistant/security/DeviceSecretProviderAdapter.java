package com.healthassistant.security;

import com.healthassistant.security.api.DeviceSecretProvider;
import com.healthassistant.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
class DeviceSecretProviderAdapter implements DeviceSecretProvider {

    private final AppProperties appProperties;

    @Override
    public Optional<byte[]> getSecret(String deviceId) {
        byte[] secret = appProperties.getHmac().getDeviceSecrets().get(deviceId);
        return Optional.ofNullable(secret);
    }
}
