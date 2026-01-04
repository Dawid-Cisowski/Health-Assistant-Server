package com.healthassistant.steps;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.events.CompensationEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.StepsEventsStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

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

        for (StoredEventData eventData : event.events()) {
            try {
                log.debug("Processing StepsBucketedRecorded event: {}", eventData.eventId().value());
                stepsProjector.projectSteps(eventData);
            } catch (Exception e) {
                log.error("Failed to project steps for event: {}", eventData.eventId().value(), e);
            }
        }

        log.info("Steps listener completed processing {} events", event.events().size());
    }

    @ApplicationModuleListener
    public void onCompensationEventsStored(CompensationEventsStoredEvent event) {
        var stepsCompensations = event.deletions().stream()
                .filter(d -> STEPS_BUCKETED_V1.equals(d.targetEventType()))
                .count();

        stepsCompensations += event.corrections().stream()
                .filter(c -> STEPS_BUCKETED_V1.equals(c.targetEventType()))
                .count();

        if (stepsCompensations > 0) {
            log.warn("Steps compensation events received ({} events) - full reprojection needed for affected dates",
                    stepsCompensations);
        }
    }
}
