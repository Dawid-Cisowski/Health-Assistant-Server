package com.healthassistant.weightimport;

final class WeightImportSecurityUtils {

    private static final int MAX_LOG_LENGTH = 100;

    private WeightImportSecurityUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() < 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "..." + deviceId.substring(deviceId.length() - 4);
    }

    static String sanitizeForLog(String input) {
        if (input == null) {
            return "null";
        }
        String sanitized = input.replaceAll("[\\r\\n\\t]", "_");
        if (sanitized.length() > MAX_LOG_LENGTH) {
            return sanitized.substring(0, MAX_LOG_LENGTH) + "...";
        }
        return sanitized;
    }
}
