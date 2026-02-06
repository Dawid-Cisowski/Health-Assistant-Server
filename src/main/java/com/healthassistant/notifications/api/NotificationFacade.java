package com.healthassistant.notifications.api;

import com.healthassistant.notifications.api.dto.RegisterFcmTokenRequest;

public interface NotificationFacade {

    void registerFcmToken(String deviceId, RegisterFcmTokenRequest request);

    void deactivateToken(String deviceId);
}
