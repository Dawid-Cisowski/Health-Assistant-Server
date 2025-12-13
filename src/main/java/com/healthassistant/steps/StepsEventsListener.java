package com.healthassistant.steps;

import com.healthassistant.healthevents.api.dto.EventsStoredEvent;
import com.healthassistant.healthevents.api.dto.StoredEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class StepsEventsListener {

    private final StepsProjector stepsProjector;

    @ApplicationModuleListener
    public void onEventsStored(EventsStoredEvent event) {
        log.info("Steps listener received EventsStoredEvent with {} events", event.events().size());

        for (StoredEventData eventData : event.events()) {
            String eventType = eventData.eventType().value();
            if ("StepsBucketedRecorded.v1".equals(eventType)) {
                try {
                    log.debug("Processing StepsBucketedRecorded event: {}", eventData.eventId().value());
                    stepsProjector.projectSteps(eventData);
                } catch (Exception e) {
                    log.error("Failed to project steps for event: {}", eventData.eventId().value(), e);
                }
            }
        }

        log.info("Steps listener completed processing {} events", event.events().size());
    }
}
