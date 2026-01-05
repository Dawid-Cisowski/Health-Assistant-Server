package com.healthassistant.config;

import com.fasterxml.jackson.core.JsonProcessingException;
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

    private static final int MASKED_DEVICE_ID_PREFIX_LENGTH = 4;

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
            } catch (JsonProcessingException e) {
                log.error("Failed to parse HMAC devices JSON: invalid JSON format", e);
                throw new IllegalStateException("Invalid HMAC_DEVICES_JSON configuration: malformed JSON", e);
            } catch (IllegalArgumentException e) {
                log.error("Failed to decode base64 secret for device", e);
                throw new IllegalStateException("Invalid HMAC_DEVICES_JSON configuration: invalid base64 encoding", e);
            }
        }

        private Map<String, String> parseDevicesJson(String json) throws JsonProcessingException {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, new TypeReference<>() {});
        }

        private void loadDeviceSecrets(Map<String, String> devices) {
            devices.forEach((deviceId, base64Secret) -> {
                byte[] secret = Base64.getDecoder().decode(base64Secret);
                deviceSecrets.put(deviceId, secret);
                log.info("Loaded HMAC secret for device: {}", maskDeviceId(deviceId));
            });
        }

        private String maskDeviceId(String deviceId) {
            if (deviceId == null || deviceId.length() <= MASKED_DEVICE_ID_PREFIX_LENGTH) {
                return "****";
            }
            return deviceId.substring(0, MASKED_DEVICE_ID_PREFIX_LENGTH) + "****";
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
