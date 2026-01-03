package com.healthassistant.assistant;

/**
 * Context holder for assistant request data.
 * Uses InheritableThreadLocal to propagate deviceId across threads
 * in reactive/async processing where tools may be called on different threads.
 */
public class AssistantContext {

    private static final InheritableThreadLocal<String> DEVICE_ID = new InheritableThreadLocal<>();

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
