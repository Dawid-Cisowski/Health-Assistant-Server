package com.healthassistant.notifications;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true")
class NotificationScheduler {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final FcmTokenRepository fcmTokenRepository;
    private final FcmService fcmService;
    private final NotificationContentService contentService;

    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/Warsaw")
    void sendDailyNotifications() {
        LocalDate yesterday = LocalDate.now(POLAND_ZONE).minusDays(1);
        log.info("Starting daily notifications for date: {}", yesterday);

        List<FcmTokenEntity> activeTokens = fcmTokenRepository.findAllActive();
        AtomicInteger sent = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        activeTokens.stream()
                .forEach(tokenEntity -> contentService.buildDailyNotification(tokenEntity.getDeviceId(), yesterday)
                        .ifPresent(content -> {
                            FcmService.SendResult result = fcmService.sendNotification(
                                    tokenEntity.getToken(), content.title(), content.body(), content.data());
                            handleSendResult(result, tokenEntity, sent, failed);
                        }));

        log.info("Daily notifications completed: {} sent, {} failed out of {} active tokens",
                sent.get(), failed.get(), activeTokens.size());
    }

    @Scheduled(cron = "0 0 8 * * MON", zone = "Europe/Warsaw")
    void sendWeeklyNotifications() {
        LocalDate today = LocalDate.now(POLAND_ZONE);
        LocalDate weekStart = today.with(DayOfWeek.MONDAY).minusWeeks(1);
        LocalDate weekEnd = weekStart.plusDays(6);
        log.info("Starting weekly notifications for period: {} to {}", weekStart, weekEnd);

        List<FcmTokenEntity> activeTokens = fcmTokenRepository.findAllActive();
        AtomicInteger sent = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        activeTokens.stream()
                .forEach(tokenEntity -> contentService.buildWeeklyNotification(tokenEntity.getDeviceId(), weekStart, weekEnd)
                        .ifPresent(content -> {
                            FcmService.SendResult result = fcmService.sendNotification(
                                    tokenEntity.getToken(), content.title(), content.body(), content.data());
                            handleSendResult(result, tokenEntity, sent, failed);
                        }));

        log.info("Weekly notifications completed: {} sent, {} failed out of {} active tokens",
                sent.get(), failed.get(), activeTokens.size());
    }

    @Scheduled(cron = "0 5 8 1 * *", zone = "Europe/Warsaw")
    void sendMonthlyNotifications() {
        LocalDate today = LocalDate.now(POLAND_ZONE);
        LocalDate monthStart = today.minusMonths(1).withDayOfMonth(1);
        LocalDate monthEnd = today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth());
        log.info("Starting monthly notifications for period: {} to {}", monthStart, monthEnd);

        List<FcmTokenEntity> activeTokens = fcmTokenRepository.findAllActive();
        AtomicInteger sent = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        activeTokens.stream()
                .forEach(tokenEntity -> contentService.buildMonthlyNotification(tokenEntity.getDeviceId(), monthStart, monthEnd)
                        .ifPresent(content -> {
                            FcmService.SendResult result = fcmService.sendNotification(
                                    tokenEntity.getToken(), content.title(), content.body(), content.data());
                            handleSendResult(result, tokenEntity, sent, failed);
                        }));

        log.info("Monthly notifications completed: {} sent, {} failed out of {} active tokens",
                sent.get(), failed.get(), activeTokens.size());
    }

    private void handleSendResult(FcmService.SendResult result, FcmTokenEntity tokenEntity,
                                  AtomicInteger sent, AtomicInteger failed) {
        if (result.success()) {
            sent.incrementAndGet();
        } else {
            failed.incrementAndGet();
            if (result.tokenInvalid()) {
                log.info("Deactivating invalid token for device {}", maskDeviceId(tokenEntity.getDeviceId()));
                tokenEntity.deactivate();
                fcmTokenRepository.save(tokenEntity);
            }
        }
    }

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "***" + deviceId.substring(deviceId.length() - 4);
    }
}
