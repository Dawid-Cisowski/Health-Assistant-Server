package com.healthassistant.config;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
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
    private final EnergyRequirementsConfig energyRequirements = new EnergyRequirementsConfig();

    @PostConstruct
    public void init() {
        hmac.init();
        energyRequirements.validate();
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
            } catch (JacksonException e) {
                log.error("Failed to parse HMAC devices JSON: invalid JSON format", e);
                throw new IllegalStateException("Invalid HMAC_DEVICES_JSON configuration: malformed JSON", e);
            } catch (IllegalArgumentException e) {
                log.error("Failed to decode base64 secret for device", e);
                throw new IllegalStateException("Invalid HMAC_DEVICES_JSON configuration: invalid base64 encoding", e);
            }
        }

        private Map<String, String> parseDevicesJson(String json) throws JacksonException {
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

    @Data
    public static class EnergyRequirementsConfig {
        private int surplusKcal = 300;
        private double baseMultiplier = 1.35;
        private int stepsThreshold = 4000;
        private int stepsInterval = 2000;
        private int kcalPerInterval = 90;
        private int maxIntervals = 8;
        private int trainingBonus = 250;
        private double proteinPerLbm = 2.8;
        private int fatTrainingDay = 50;
        private int fatRestDay = 80;

        public void validate() {
            if (surplusKcal < 0 || surplusKcal > 1000) {
                throw new IllegalStateException("surplusKcal must be between 0 and 1000, got: " + surplusKcal);
            }
            if (baseMultiplier < 1.0 || baseMultiplier > 3.0) {
                throw new IllegalStateException("baseMultiplier must be between 1.0 and 3.0, got: " + baseMultiplier);
            }
            if (stepsThreshold < 0) {
                throw new IllegalStateException("stepsThreshold cannot be negative, got: " + stepsThreshold);
            }
            if (stepsInterval <= 0) {
                throw new IllegalStateException("stepsInterval must be positive, got: " + stepsInterval);
            }
            if (kcalPerInterval < 0) {
                throw new IllegalStateException("kcalPerInterval cannot be negative, got: " + kcalPerInterval);
            }
            if (maxIntervals < 0) {
                throw new IllegalStateException("maxIntervals cannot be negative, got: " + maxIntervals);
            }
            if (trainingBonus < 0) {
                throw new IllegalStateException("trainingBonus cannot be negative, got: " + trainingBonus);
            }
            if (proteinPerLbm < 0.5 || proteinPerLbm > 5.0) {
                throw new IllegalStateException("proteinPerLbm must be between 0.5 and 5.0 g/kg, got: " + proteinPerLbm);
            }
            if (fatTrainingDay < 0) {
                throw new IllegalStateException("fatTrainingDay cannot be negative, got: " + fatTrainingDay);
            }
            if (fatRestDay < 0) {
                throw new IllegalStateException("fatRestDay cannot be negative, got: " + fatRestDay);
            }
        }
    }
}
