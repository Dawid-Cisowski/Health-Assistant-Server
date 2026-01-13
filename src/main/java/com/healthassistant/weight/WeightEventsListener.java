package com.healthassistant.weight;

import com.healthassistant.healthevents.api.dto.events.CompensationEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.WeightEventsStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
class WeightEventsListener {

    private static final String WEIGHT_MEASUREMENT_V1 = "WeightMeasurementRecorded.v1";

    private final WeightProjector weightProjector;

    @ApplicationModuleListener
    @Transactional
    public void onWeightEventsStored(WeightEventsStoredEvent event) {
        log.info("Weight listener received WeightEventsStoredEvent with {} events for {} dates",
                event.events().size(), event.affectedDates().size());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        event.events().forEach(eventData -> {
            try {
                log.debug("Processing WeightMeasurementRecorded event: {}", eventData.eventId().value());
                weightProjector.projectWeight(eventData);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
                log.error("Failed to project weight for event: {}", eventData.eventId().value(), e);
            }
        });

        if (failureCount.get() > 0) {
            log.error("Weight projection completed with failures: {} succeeded, {} failed",
                    successCount.get(), failureCount.get());
        } else {
            log.info("Weight listener completed processing {} events successfully", successCount.get());
        }
    }

    @ApplicationModuleListener
    @Transactional
    public void onCompensationEventsStored(CompensationEventsStoredEvent event) {
        var weightDeletions = event.deletions().stream()
                .filter(d -> WEIGHT_MEASUREMENT_V1.equals(d.targetEventType()))
                .toList();

        if (weightDeletions.isEmpty()) {
            return;
        }

        log.info("Weight listener processing {} deletions", weightDeletions.size());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        weightDeletions.forEach(deletion -> {
            try {
                weightProjector.deleteByEventId(deletion.targetEventId());
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
                log.error("Failed to delete weight projection for eventId: {}", deletion.targetEventId(), e);
            }
        });

        if (failureCount.get() > 0) {
            log.error("Weight deletion completed with failures: {} succeeded, {} failed",
                    successCount.get(), failureCount.get());
        }
    }
}
