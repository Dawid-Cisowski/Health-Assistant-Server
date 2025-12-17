package com.healthassistant.activity;

import com.healthassistant.healthevents.api.dto.events.ActivityEventsStoredEvent;
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
    public void onActivityEventsStored(ActivityEventsStoredEvent event) {
        log.info("Activity listener received ActivityEventsStoredEvent with {} events for {} dates",
                event.events().size(), event.affectedDates().size());

        event.events().forEach(eventData -> {
            log.debug("Processing ActiveMinutesRecorded event: {}", eventData.eventId().value());
            activityProjector.projectActivity(eventData);
        });

        log.info("Activity listener completed processing {} events", event.events().size());
    }
}
