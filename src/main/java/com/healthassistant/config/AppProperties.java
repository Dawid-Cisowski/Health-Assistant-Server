package com.healthassistant.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "app")
@Slf4j
@Getter
public class AppProperties {

    private final HmacConfig hmac = new HmacConfig();
    private final NonceConfig nonce = new NonceConfig();
    private final GoogleFitConfig googleFit = new GoogleFitConfig();

    @PostConstruct
    public void init() {
        hmac.init();
    }

    @Data
    public static class HmacConfig {
        private String devicesJson;
        private int toleranceSeconds = 600;
        private Map<String, byte[]> deviceSecrets = new HashMap<>();

        public void init() {
            if (devicesJson == null || devicesJson.isBlank()) {
                return;
            }

            try {
                Map<String, String> devices = parseDevicesJson(devicesJson);
                loadDeviceSecrets(devices);
            } catch (Exception e) {
                log.error("Failed to parse HMAC devices JSON", e);
                throw new IllegalStateException("Invalid HMAC_DEVICES_JSON configuration", e);
            }
        }

        private Map<String, String> parseDevicesJson(String json) throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, new TypeReference<>() {});
        }

        private void loadDeviceSecrets(Map<String, String> devices) {
            devices.forEach((deviceId, base64Secret) -> {
                byte[] secret = Base64.getDecoder().decode(base64Secret);
                deviceSecrets.put(deviceId, secret);
                log.info("Loaded HMAC secret for device: {}", deviceId);
            });
        }
    }

    @Data
    public static class NonceConfig {
        private int cacheTtlSeconds = 600;
    }

    @Data
    public static class GoogleFitConfig {
        private String clientId;
        private String clientSecret;
        private String refreshToken;
        private String apiUrl;
        private String oauthUrl;
        private String deviceId = "google-fit";
    }
}

