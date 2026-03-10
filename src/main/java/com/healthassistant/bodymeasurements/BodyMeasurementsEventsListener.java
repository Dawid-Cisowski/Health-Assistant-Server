package com.healthassistant.bodymeasurements;

import com.healthassistant.config.SecurityUtils;
import com.healthassistant.healthevents.api.dto.events.BodyMeasurementsEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.CompensationEventsStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class BodyMeasurementsEventsListener {

    private static final String BODY_MEASUREMENT_V1 = "BodyMeasurementRecorded.v1";

    private final BodyMeasurementsProjector bodyMeasurementsProjector;

    @ApplicationModuleListener
    public void onBodyMeasurementsEventsStored(BodyMeasurementsEventsStoredEvent event) {
        log.info("Body measurements listener received BodyMeasurementsEventsStoredEvent with {} events for {} dates",
                event.events().size(), event.affectedDates().size());

        var results = event.events().stream()
                .map(eventData -> {
                    try {
                        log.debug("Processing BodyMeasurementRecorded event: {}", SecurityUtils.sanitizeForLog(eventData.eventId().value()));
                        bodyMeasurementsProjector.projectBodyMeasurement(eventData);
                        return true;
                    } catch (Exception e) {
                        log.error("Failed to project body measurement for event: {}", SecurityUtils.sanitizeForLog(eventData.eventId().value()), e);
                        return false;
                    }
                })
                .toList();

        long successCount = results.stream().filter(Boolean::booleanValue).count();
        long failureCount = results.size() - successCount;

        if (failureCount > 0) {
            log.error("Body measurements projection completed with failures: {} succeeded, {} failed",
                    successCount, failureCount);
        } else {
            log.info("Body measurements listener completed processing {} events successfully", successCount);
        }
    }

    @ApplicationModuleListener
    public void onCompensationEventsStored(CompensationEventsStoredEvent event) {
        var bodyMeasurementDeletions = event.deletions().stream()
                .filter(d -> BODY_MEASUREMENT_V1.equals(d.targetEventType()))
                .toList();

        var bodyMeasurementCorrections = event.corrections().stream()
                .filter(c -> BODY_MEASUREMENT_V1.equals(c.targetEventType()) || BODY_MEASUREMENT_V1.equals(c.correctedEventType()))
                .toList();

        if (bodyMeasurementDeletions.isEmpty() && bodyMeasurementCorrections.isEmpty()) {
            return;
        }

        log.info("Body measurements listener processing {} deletions and {} corrections",
                bodyMeasurementDeletions.size(), bodyMeasurementCorrections.size());

        bodyMeasurementDeletions.forEach(deletion -> {
            try {
                bodyMeasurementsProjector.deleteByEventId(deletion.targetEventId());
            } catch (Exception e) {
                log.error("Failed to delete body measurement projection for eventId: {}", SecurityUtils.sanitizeForLog(deletion.targetEventId()), e);
            }
        });

        bodyMeasurementCorrections.forEach(correction -> {
            try {
                bodyMeasurementsProjector.deleteByEventId(correction.targetEventId());
                if (BODY_MEASUREMENT_V1.equals(correction.correctedEventType()) && correction.correctedPayload() != null) {
                    bodyMeasurementsProjector.projectCorrectedBodyMeasurement(
                            event.deviceId(),
                            correction.correctedPayload(),
                            correction.correctedOccurredAt()
                    );
                }
            } catch (Exception e) {
                log.error("Failed to process correction for body measurement eventId: {}", SecurityUtils.sanitizeForLog(correction.targetEventId()), e);
            }
        });
    }
}
