package com.healthassistant.notifications;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true")
class FcmService {

    private final FirebaseMessaging firebaseMessaging;

    record SendResult(boolean success, boolean tokenInvalid) {}

    SendResult sendNotification(String fcmToken, String title, String body, Map<String, String> data) {
        Notification notification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(notification)
                .putAllData(data)
                .build();

        try {
            String messageId = firebaseMessaging.send(message);
            log.debug("FCM message sent successfully, messageId: {}", messageId);
            return new SendResult(true, false);
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode errorCode = e.getMessagingErrorCode();
            if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("FCM token invalid (error: {}), will deactivate", errorCode);
                return new SendResult(false, true);
            }
            log.error("Failed to send FCM notification (error: {})", errorCode, e);
            return new SendResult(false, false);
        }
    }
}
