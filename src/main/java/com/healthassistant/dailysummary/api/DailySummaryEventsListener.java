package com.healthassistant.dailysummary.api;

import com.healthassistant.healthevents.api.dto.events.AllEventsStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
class DailySummaryEventsListener {

    private final DailySummaryFacade dailySummaryFacade;

    @ApplicationModuleListener
    public void onAllEventsStored(AllEventsStoredEvent event) {
        log.info("DailySummary listener received AllEventsStoredEvent for device {} with {} affected dates, {} event types",
                maskDeviceId(event.deviceId()), event.affectedDates().size(), event.eventTypes().size());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        event.affectedDates().forEach(date -> processDate(event.deviceId(), date, successCount, failureCount));

        log.info("DailySummary listener completed for device {}: {} succeeded, {} failed",
                maskDeviceId(event.deviceId()), successCount.get(), failureCount.get());
    }

    private void processDate(String deviceId, LocalDate date, AtomicInteger successCount, AtomicInteger failureCount) {
        try {
            log.debug("Regenerating daily summary for device {} date: {}", maskDeviceId(deviceId), date);
            dailySummaryFacade.generateDailySummary(deviceId, date);
            successCount.incrementAndGet();
            log.debug("Successfully regenerated daily summary for device {} date: {}", maskDeviceId(deviceId), date);
        } catch (Exception e) {
            failureCount.incrementAndGet();
            log.error("Failed to regenerate daily summary for device {} date: {}", maskDeviceId(deviceId), date, e);
        }
    }

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "***" + deviceId.substring(deviceId.length() - 4);
    }
}
