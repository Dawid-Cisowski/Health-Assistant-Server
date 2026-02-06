package com.healthassistant.notifications;

import com.healthassistant.notifications.api.NotificationFacade;
import com.healthassistant.notifications.api.dto.RegisterFcmTokenRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
class NotificationService implements NotificationFacade {

    private final FcmTokenRepository fcmTokenRepository;

    @Override
    @Transactional
    public void registerFcmToken(String deviceId, RegisterFcmTokenRequest request) {
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        log.info("Registering FCM token for device {}", maskDeviceId(deviceId));

        fcmTokenRepository.findByDeviceId(deviceId)
                .ifPresentOrElse(
                        existing -> {
                            existing.updateToken(request.token());
                            fcmTokenRepository.save(existing);
                            log.info("Updated FCM token for device {}", maskDeviceId(deviceId));
                        },
                        () -> {
                            FcmTokenEntity entity = FcmTokenEntity.createForDevice(deviceId, request.token());
                            fcmTokenRepository.save(entity);
                            log.info("Created FCM token for device {}", maskDeviceId(deviceId));
                        }
                );
    }

    @Override
    @Transactional
    public void deactivateToken(String deviceId) {
        Objects.requireNonNull(deviceId, "deviceId must not be null");

        log.info("Deactivating FCM token for device {}", maskDeviceId(deviceId));

        fcmTokenRepository.findByDeviceId(deviceId)
                .ifPresent(entity -> {
                    entity.deactivate();
                    fcmTokenRepository.save(entity);
                    log.info("Deactivated FCM token for device {}", maskDeviceId(deviceId));
                });
    }

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "***" + deviceId.substring(deviceId.length() - 4);
    }
}
