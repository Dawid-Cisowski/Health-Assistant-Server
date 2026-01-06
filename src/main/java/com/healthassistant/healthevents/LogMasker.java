package com.healthassistant.healthevents;

final class LogMasker {

    private static final int VISIBLE_PREFIX_LENGTH = 8;
    private static final String MASK = "***";

    private LogMasker() {
    }

    static String maskSensitive(String value) {
        if (value == null) {
            return "null";
        }
        if (value.length() <= VISIBLE_PREFIX_LENGTH) {
            return MASK;
        }
        return value.substring(0, VISIBLE_PREFIX_LENGTH) + MASK;
    }
}
