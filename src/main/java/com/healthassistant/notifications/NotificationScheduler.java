package com.healthassistant.notifications;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

@Service
@Slf4j
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true")
class NotificationScheduler {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final FcmService fcmService;
    private final NotificationContentService contentService;
    private final String fcmToken;
    private final String deviceId;

    NotificationScheduler(FcmService fcmService,
                          NotificationContentService contentService,
                          @Value("${app.notifications.fcm-token}") String fcmToken,
                          @Value("${app.notifications.device-id}") String deviceId) {
        this.fcmService = fcmService;
        this.contentService = contentService;
        this.fcmToken = fcmToken;
        this.deviceId = deviceId;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/Warsaw")
    void sendDailyNotifications() {
        LocalDate yesterday = LocalDate.now(POLAND_ZONE).minusDays(1);
        log.info("Starting daily notification for date: {}", yesterday);

        sendNotification(contentService.buildDailyNotification(deviceId, yesterday), "Daily");
    }

    @Scheduled(cron = "0 0 8 * * MON", zone = "Europe/Warsaw")
    void sendWeeklyNotifications() {
        LocalDate today = LocalDate.now(POLAND_ZONE);
        LocalDate weekStart = today.with(DayOfWeek.MONDAY).minusWeeks(1);
        LocalDate weekEnd = weekStart.plusDays(6);
        log.info("Starting weekly notification for period: {} to {}", weekStart, weekEnd);

        sendNotification(contentService.buildWeeklyNotification(deviceId, weekStart, weekEnd), "Weekly");
    }

    @Scheduled(cron = "0 5 8 1 * *", zone = "Europe/Warsaw")
    void sendMonthlyNotifications() {
        LocalDate today = LocalDate.now(POLAND_ZONE);
        LocalDate monthStart = today.minusMonths(1).withDayOfMonth(1);
        LocalDate monthEnd = today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth());
        log.info("Starting monthly notification for period: {} to {}", monthStart, monthEnd);

        sendNotification(contentService.buildMonthlyNotification(deviceId, monthStart, monthEnd), "Monthly");
    }

    private void sendNotification(Optional<NotificationContentService.NotificationContent> contentOpt, String type) {
        contentOpt.ifPresentOrElse(
                content -> {
                    FcmService.SendResult result = fcmService.sendNotification(fcmToken, content.title(), content.body(), content.data());
                    if (result.success()) {
                        log.info("{} notification sent successfully", type);
                    } else {
                        log.warn("{} notification failed to send (tokenInvalid: {})", type, result.tokenInvalid());
                    }
                },
                () -> log.info("{} notification skipped — no data available", type)
        );
    }
}
