package com.healthassistant.notifications;

import com.healthassistant.reports.api.ReportsFacade;
import com.healthassistant.reports.api.dto.HealthReportDetailResponse;
import com.healthassistant.reports.api.dto.ReportType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true")
class NotificationScheduler {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final FcmService fcmService;
    private final ReportsFacade reportsFacade;
    private final String fcmToken;
    private final String deviceId;

    NotificationScheduler(FcmService fcmService,
                          ReportsFacade reportsFacade,
                          @Value("${app.notifications.fcm-token}") String fcmToken,
                          @Value("${app.notifications.device-id}") String deviceId) {
        this.fcmService = fcmService;
        this.reportsFacade = reportsFacade;
        this.fcmToken = fcmToken;
        this.deviceId = deviceId;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/Warsaw")
    void sendDailyNotifications() {
        LocalDate yesterday = LocalDate.now(POLAND_ZONE).minusDays(1);
        log.info("Starting daily notification for date: {}", yesterday);

        generateAndNotify(ReportType.DAILY, yesterday, yesterday, "Daily");
    }

    @Scheduled(cron = "0 0 8 * * MON", zone = "Europe/Warsaw")
    void sendWeeklyNotifications() {
        LocalDate today = LocalDate.now(POLAND_ZONE);
        LocalDate weekStart = today.with(DayOfWeek.MONDAY).minusWeeks(1);
        LocalDate weekEnd = weekStart.plusDays(6);
        log.info("Starting weekly notification for period: {} to {}", weekStart, weekEnd);

        generateAndNotify(ReportType.WEEKLY, weekStart, weekEnd, "Weekly");
    }

    @Scheduled(cron = "0 5 8 1 * *", zone = "Europe/Warsaw")
    void sendMonthlyNotifications() {
        LocalDate today = LocalDate.now(POLAND_ZONE);
        LocalDate monthStart = today.minusMonths(1).withDayOfMonth(1);
        LocalDate monthEnd = today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth());
        log.info("Starting monthly notification for period: {} to {}", monthStart, monthEnd);

        generateAndNotify(ReportType.MONTHLY, monthStart, monthEnd, "Monthly");
    }

    private void generateAndNotify(ReportType type, LocalDate periodStart, LocalDate periodEnd, String label) {
        try {
            Optional<Long> reportIdOpt = reportsFacade.generateReport(deviceId, type, periodStart, periodEnd);
            if (reportIdOpt.isEmpty()) {
                log.info("{} notification skipped — no data available", label);
                return;
            }

            long reportId = reportIdOpt.get();
            Optional<HealthReportDetailResponse> reportOpt = reportsFacade.getReport(deviceId, reportId);
            if (reportOpt.isEmpty()) {
                log.warn("{} notification skipped — report not found after generation", label);
                return;
            }

            HealthReportDetailResponse report = reportOpt.get();
            String title = buildTitle(type);
            String body = buildBody(report);
            Map<String, String> data = Map.of(
                    "type", type.name() + "_REPORT",
                    "reportId", String.valueOf(reportId),
                    "goalsAchieved", String.valueOf(report.goals() != null ? report.goals().achieved() : 0),
                    "goalsTotal", String.valueOf(report.goals() != null ? report.goals().total() : 0)
            );

            FcmService.SendResult result = fcmService.sendNotification(fcmToken, title, body, data);
            if (result.success()) {
                log.info("{} notification sent successfully (reportId={})", label, reportId);
            } else {
                log.warn("{} notification failed to send (tokenInvalid: {})", label, result.tokenInvalid());
            }
        } catch (Exception e) {
            log.error("{} notification failed", label, e);
        }
    }

    private String buildTitle(ReportType type) {
        return switch (type) {
            case DAILY -> "Podsumowanie dnia";
            case WEEKLY -> "Podsumowanie tygodnia";
            case MONTHLY -> "Podsumowanie miesiaca";
        };
    }

    private String buildBody(HealthReportDetailResponse report) {
        if (report.goals() != null) {
            return "Osiagnales " + report.goals().achieved() + "/" + report.goals().total()
                    + " celow! " + (report.shortSummary() != null ? report.shortSummary() : "");
        }
        return report.shortSummary() != null ? report.shortSummary() : "Sprawdz swoj raport zdrowotny";
    }
}
