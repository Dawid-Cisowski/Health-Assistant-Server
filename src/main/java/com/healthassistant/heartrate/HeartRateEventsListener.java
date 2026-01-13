package com.healthassistant.heartrate;

import com.healthassistant.healthevents.api.dto.events.CompensationEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.HeartRateEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.RestingHeartRateEventsStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
class HeartRateEventsListener {

    private static final String HEART_RATE_SUMMARY_V1 = "HeartRateSummaryRecorded.v1";
    private static final String RESTING_HR_V1 = "RestingHeartRateRecorded.v1";

    private final HeartRateProjector heartRateProjector;

    @ApplicationModuleListener
    public void onHeartRateEventsStored(HeartRateEventsStoredEvent event) {
        log.info("HR listener received HeartRateEventsStoredEvent with {} events for {} dates",
                event.events().size(), event.affectedDates().size());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        event.events().forEach(eventData -> {
            try {
                log.debug("Processing HeartRateSummaryRecorded event: {}", eventData.eventId().value());
                heartRateProjector.projectHeartRateSummary(eventData);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
                log.error("Failed to project HR for event: {}", eventData.eventId().value(), e);
            }
        });

        if (failureCount.get() > 0) {
            log.error("HR projection completed with failures: {} succeeded, {} failed",
                    successCount.get(), failureCount.get());
        } else {
            log.info("HR listener completed processing {} events successfully", successCount.get());
        }
    }

    @ApplicationModuleListener
    public void onRestingHeartRateEventsStored(RestingHeartRateEventsStoredEvent event) {
        log.info("Resting HR listener received {} events for {} dates",
                event.events().size(), event.affectedDates().size());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        event.events().forEach(eventData -> {
            try {
                log.debug("Processing RestingHeartRateRecorded event: {}", eventData.eventId().value());
                heartRateProjector.projectRestingHeartRate(eventData);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
                log.error("Failed to project resting HR for event: {}", eventData.eventId().value(), e);
            }
        });

        if (failureCount.get() > 0) {
            log.error("Resting HR projection completed with failures: {} succeeded, {} failed",
                    successCount.get(), failureCount.get());
        } else {
            log.info("Resting HR listener completed processing {} events successfully", successCount.get());
        }
    }

    @ApplicationModuleListener
    public void onCompensationEventsStored(CompensationEventsStoredEvent event) {
        var hrDeletions = event.deletions().stream()
                .filter(d -> HEART_RATE_SUMMARY_V1.equals(d.targetEventType())
                        || RESTING_HR_V1.equals(d.targetEventType()))
                .toList();

        if (hrDeletions.isEmpty()) {
            return;
        }

        log.info("HR listener processing {} deletions", hrDeletions.size());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        hrDeletions.forEach(deletion -> {
            try {
                heartRateProjector.deleteByEventId(deletion.targetEventId());
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
                log.error("Failed to delete HR projection for eventId: {}", deletion.targetEventId(), e);
            }
        });

        if (failureCount.get() > 0) {
            log.error("HR deletion completed with failures: {} succeeded, {} failed",
                    successCount.get(), failureCount.get());
        }
    }
}
