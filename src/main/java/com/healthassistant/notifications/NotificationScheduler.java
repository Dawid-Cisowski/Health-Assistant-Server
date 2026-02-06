package com.healthassistant.notifications;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true")
class NotificationScheduler {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final FcmTokenRepository fcmTokenRepository;
    private final FcmService fcmService;
    private final NotificationContentService contentService;

    private record NotificationResult(boolean sent, boolean tokenInvalid, String deviceId) {}

    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/Warsaw")
    void sendDailyNotifications() {
        LocalDate yesterday = LocalDate.now(POLAND_ZONE).minusDays(1);
        log.info("Starting daily notifications for date: {}", yesterday);

        List<FcmTokenEntity> activeTokens = fcmTokenRepository.findAllActive();

        List<NotificationResult> results = activeTokens.stream()
                .map(token -> sendNotification(token, contentService.buildDailyNotification(token.getDeviceId(), yesterday)))
                .toList();

        logResults("Daily", results, activeTokens.size());
        deactivateInvalidTokens(results);
    }

    @Scheduled(cron = "0 0 8 * * MON", zone = "Europe/Warsaw")
    void sendWeeklyNotifications() {
        LocalDate today = LocalDate.now(POLAND_ZONE);
        LocalDate weekStart = today.with(DayOfWeek.MONDAY).minusWeeks(1);
        LocalDate weekEnd = weekStart.plusDays(6);
        log.info("Starting weekly notifications for period: {} to {}", weekStart, weekEnd);

        List<FcmTokenEntity> activeTokens = fcmTokenRepository.findAllActive();

        List<NotificationResult> results = activeTokens.stream()
                .map(token -> sendNotification(token, contentService.buildWeeklyNotification(token.getDeviceId(), weekStart, weekEnd)))
                .toList();

        logResults("Weekly", results, activeTokens.size());
        deactivateInvalidTokens(results);
    }

    @Scheduled(cron = "0 5 8 1 * *", zone = "Europe/Warsaw")
    void sendMonthlyNotifications() {
        LocalDate today = LocalDate.now(POLAND_ZONE);
        LocalDate monthStart = today.minusMonths(1).withDayOfMonth(1);
        LocalDate monthEnd = today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth());
        log.info("Starting monthly notifications for period: {} to {}", monthStart, monthEnd);

        List<FcmTokenEntity> activeTokens = fcmTokenRepository.findAllActive();

        List<NotificationResult> results = activeTokens.stream()
                .map(token -> sendNotification(token, contentService.buildMonthlyNotification(token.getDeviceId(), monthStart, monthEnd)))
                .toList();

        logResults("Monthly", results, activeTokens.size());
        deactivateInvalidTokens(results);
    }

    private NotificationResult sendNotification(FcmTokenEntity token,
                                                 Optional<NotificationContentService.NotificationContent> contentOpt) {
        return contentOpt
                .map(content -> {
                    FcmService.SendResult result = fcmService.sendNotification(
                            token.getToken(), content.title(), content.body(), content.data());
                    return new NotificationResult(result.success(), result.tokenInvalid(), token.getDeviceId());
                })
                .orElse(new NotificationResult(false, false, token.getDeviceId()));
    }

    private void deactivateInvalidTokens(List<NotificationResult> results) {
        results.stream()
                .filter(r -> r.tokenInvalid())
                .map(NotificationResult::deviceId)
                .forEach(this::deactivateTokenSafely);
    }

    @Transactional
    void deactivateTokenSafely(String deviceId) {
        try {
            fcmTokenRepository.findByDeviceId(deviceId)
                    .ifPresent(entity -> {
                        log.info("Deactivating invalid token for device {}", maskDeviceId(deviceId));
                        entity.deactivate();
                        fcmTokenRepository.save(entity);
                    });
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Token already modified for device {}, skipping deactivation", maskDeviceId(deviceId));
        }
    }

    private void logResults(String type, List<NotificationResult> results, int totalTokens) {
        long sent = results.stream().filter(NotificationResult::sent).count();
        long failed = results.size() - sent;
        log.info("{} notifications completed: {} sent, {} failed out of {} active tokens",
                type, sent, failed, totalTokens);
    }

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "***" + deviceId.substring(deviceId.length() - 4);
    }
}
