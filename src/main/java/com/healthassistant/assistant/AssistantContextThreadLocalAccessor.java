package com.healthassistant.assistant;

import io.micrometer.context.ThreadLocalAccessor;

/**
 * ThreadLocalAccessor for propagating AssistantContext device ID across reactive boundaries.
 *
 * <p>This is necessary because Spring AI's tool invocation happens on different threads
 * from Reactor's boundedElastic scheduler pool, and InheritableThreadLocal doesn't
 * propagate to reused threads.
 *
 * <p>With context-propagation enabled, this accessor ensures the device ID is captured
 * from the reactive context and restored on whichever thread executes the tool call.
 */
public class AssistantContextThreadLocalAccessor implements ThreadLocalAccessor<String> {

    public static final String KEY = "assistant.deviceId";

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public String getValue() {
        return AssistantContext.getDeviceId();
    }

    @Override
    public void setValue(String deviceId) {
        AssistantContext.setDeviceId(deviceId);
    }

    @Override
    public void setValue() {
        AssistantContext.clear();
    }
}
