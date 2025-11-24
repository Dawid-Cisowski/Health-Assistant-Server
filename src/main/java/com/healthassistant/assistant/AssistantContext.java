package com.healthassistant.assistant;

public class AssistantContext {

    private static final ThreadLocal<String> DEVICE_ID = new ThreadLocal<>();

    public static void setDeviceId(String deviceId) {
        DEVICE_ID.set(deviceId);
    }

    public static String getDeviceId() {
        return DEVICE_ID.get();
    }

    public static void clear() {
        DEVICE_ID.remove();
    }
}
