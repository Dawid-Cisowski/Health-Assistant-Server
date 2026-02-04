package com.healthassistant.config;

/**
 * Shared security utilities for log sanitization and data masking.
 */
public final class SecurityUtils {

    private static final int MAX_LOG_LENGTH = 100;

    private SecurityUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Masks a device ID for safe logging, showing only first and last 4 characters.
     *
     * @param deviceId the device ID to mask
     * @return masked device ID (e.g., "abcd...wxyz") or "***" if too short/null
     */
    public static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() < 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "..." + deviceId.substring(deviceId.length() - 4);
    }

    /**
     * Sanitizes user input for safe logging by removing control characters
     * and truncating to maximum length.
     *
     * @param input the input to sanitize
     * @return sanitized string safe for logging
     */
    public static String sanitizeForLog(String input) {
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
