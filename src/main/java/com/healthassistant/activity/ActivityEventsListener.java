package com.healthassistant.activity;

import com.healthassistant.healthevents.api.dto.events.ActivityEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.CompensationEventsStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class ActivityEventsListener {

    private static final String ACTIVE_MINUTES_V1 = "ActiveMinutesRecorded.v1";

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

    @ApplicationModuleListener
    public void onCompensationEventsStored(CompensationEventsStoredEvent event) {
        var activityCompensations = event.deletions().stream()
                .filter(d -> ACTIVE_MINUTES_V1.equals(d.targetEventType()))
                .count();

        activityCompensations += event.corrections().stream()
                .filter(c -> ACTIVE_MINUTES_V1.equals(c.targetEventType()))
                .count();

        if (activityCompensations > 0) {
            log.warn("Activity compensation events received ({} events) - full reprojection needed for affected dates",
                    activityCompensations);
        }
    }
}
