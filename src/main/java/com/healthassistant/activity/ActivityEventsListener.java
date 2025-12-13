package com.healthassistant.activity;

import com.healthassistant.healthevents.api.dto.EventsStoredEvent;
import com.healthassistant.healthevents.api.dto.StoredEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class ActivityEventsListener {

    private final ActivityProjector activityProjector;

    @ApplicationModuleListener
    public void onEventsStored(EventsStoredEvent event) {
        log.info("Activity listener received EventsStoredEvent with {} events", event.events().size());

        for (StoredEventData eventData : event.events()) {
            String eventType = eventData.eventType().value();
            if ("ActiveMinutesRecorded.v1".equals(eventType)) {
                try {
                    log.debug("Processing ActiveMinutesRecorded event: {}", eventData.eventId().value());
                    activityProjector.projectActivity(eventData);
                } catch (Exception e) {
                    log.error("Failed to project activity for event: {}", eventData.eventId().value(), e);
                }
            }
        }

        log.info("Activity listener completed processing {} events", event.events().size());
    }
}
