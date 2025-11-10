package com.healthassistant.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Application-specific configuration properties
 */
@Configuration
@ConfigurationProperties(prefix = "app")
@Data
@Slf4j
public class AppProperties {

    private final HmacConfig hmac = new HmacConfig();
    private final NonceConfig nonce = new NonceConfig();
    private final BatchConfig batch = new BatchConfig();

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
            log.info("devicesJson: {}", devicesJson);
            log.info("deviceSecrets: {}", deviceSecrets);

            if (devicesJson != null && !devicesJson.isBlank()) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, String> devices = mapper.readValue(
                        devicesJson,
                        new TypeReference<Map<String, String>>() {}
                    );

                    for (Map.Entry<String, String> entry : devices.entrySet()) {
                        String deviceId = entry.getKey();
                        String base64Secret = entry.getValue();
                        byte[] secret = Base64.getDecoder().decode(base64Secret);
                        deviceSecrets.put(deviceId, secret);
                        log.info("Loaded HMAC secret for device: {}", deviceId);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse HMAC devices JSON", e);
                    throw new IllegalStateException("Invalid HMAC_DEVICES_JSON configuration", e);
                }
            }
        }
    }

    @Data
    public static class NonceConfig {
        private int cacheTtlSeconds = 600;
    }

    @Data
    public static class BatchConfig {
        private int maxEvents = 100;
    }
}

