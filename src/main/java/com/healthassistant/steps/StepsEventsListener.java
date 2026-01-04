package com.healthassistant.steps;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.events.CompensationEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.StepsEventsStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
class StepsEventsListener {

    private static final String STEPS_BUCKETED_V1 = "StepsBucketedRecorded.v1";

    private final StepsProjector stepsProjector;

    @ApplicationModuleListener
    public void onStepsEventsStored(StepsEventsStoredEvent event) {
        log.info("Steps listener received StepsEventsStoredEvent with {} events for {} dates",
                event.events().size(), event.affectedDates().size());

        event.events().forEach(eventData -> {
            try {
                log.debug("Processing StepsBucketedRecorded event: {}", eventData.eventId().value());
                stepsProjector.projectSteps(eventData);
            } catch (Exception e) {
                log.error("Failed to project steps for event: {}", eventData.eventId().value(), e);
            }
        });

        log.info("Steps listener completed processing {} events", event.events().size());
    }

    @ApplicationModuleListener
    public void onCompensationEventsStored(CompensationEventsStoredEvent event) {
        var stepsDeletions = event.deletions().stream()
                .filter(d -> STEPS_BUCKETED_V1.equals(d.targetEventType()))
                .toList();

        var stepsCorrections = event.corrections().stream()
                .filter(c -> STEPS_BUCKETED_V1.equals(c.targetEventType()) || STEPS_BUCKETED_V1.equals(c.correctedEventType()))
                .toList();

        if (stepsDeletions.isEmpty() && stepsCorrections.isEmpty()) {
            return;
        }

        log.info("Steps listener processing {} deletions and {} corrections",
                stepsDeletions.size(), stepsCorrections.size());

        Set<LocalDate> affectedDates = new HashSet<>();

        stepsDeletions.forEach(deletion -> {
            affectedDates.addAll(deletion.affectedDates());
        });

        stepsCorrections.forEach(correction -> {
            affectedDates.addAll(correction.affectedDates());
            if (STEPS_BUCKETED_V1.equals(correction.correctedEventType()) && correction.correctedPayload() != null) {
                try {
                    stepsProjector.projectCorrectedSteps(
                            event.deviceId(),
                            correction.correctedPayload(),
                            correction.correctedOccurredAt()
                    );
                } catch (Exception e) {
                    log.error("Failed to project corrected steps: {}", e.getMessage(), e);
                }
            }
        });

        affectedDates.forEach(date -> {
            try {
                stepsProjector.reprojectForDate(event.deviceId(), date);
            } catch (Exception e) {
                log.error("Failed to reproject steps for date {}: {}", date, e.getMessage(), e);
            }
        });
    }
}
