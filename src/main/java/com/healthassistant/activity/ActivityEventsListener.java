package com.healthassistant.activity;

import com.healthassistant.healthevents.api.dto.events.ActivityEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.CompensationEventsStoredEvent;
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
class ActivityEventsListener {

    private static final String ACTIVE_MINUTES_V1 = "ActiveMinutesRecorded.v1";

    private final ActivityProjector activityProjector;

    @ApplicationModuleListener
    public void onActivityEventsStored(ActivityEventsStoredEvent event) {
        log.info("Activity listener received ActivityEventsStoredEvent with {} events for {} dates",
                event.events().size(), event.affectedDates().size());

        event.events().forEach(eventData -> {
            try {
                log.debug("Processing ActiveMinutesRecorded event: {}", eventData.eventId().value());
                activityProjector.projectActivity(eventData);
            } catch (Exception e) {
                log.error("Failed to project activity for event: {}", eventData.eventId().value(), e);
            }
        });

        log.info("Activity listener completed processing {} events", event.events().size());
    }

    @ApplicationModuleListener
    public void onCompensationEventsStored(CompensationEventsStoredEvent event) {
        var activityDeletions = event.deletions().stream()
                .filter(d -> ACTIVE_MINUTES_V1.equals(d.targetEventType()))
                .toList();

        var activityCorrections = event.corrections().stream()
                .filter(c -> ACTIVE_MINUTES_V1.equals(c.targetEventType()) || ACTIVE_MINUTES_V1.equals(c.correctedEventType()))
                .toList();

        if (activityDeletions.isEmpty() && activityCorrections.isEmpty()) {
            return;
        }

        log.info("Activity listener processing {} deletions and {} corrections",
                activityDeletions.size(), activityCorrections.size());

        Set<LocalDate> affectedDates = new HashSet<>();

        activityDeletions.forEach(deletion -> {
            affectedDates.addAll(deletion.affectedDates());
        });

        activityCorrections.forEach(correction -> {
            affectedDates.addAll(correction.affectedDates());
            if (ACTIVE_MINUTES_V1.equals(correction.correctedEventType()) && correction.correctedPayload() != null) {
                try {
                    activityProjector.projectCorrectedActivity(
                            event.deviceId(),
                            correction.correctedPayload(),
                            correction.correctedOccurredAt()
                    );
                } catch (Exception e) {
                    log.error("Failed to project corrected activity: {}", e.getMessage(), e);
                }
            }
        });

        affectedDates.forEach(date -> {
            try {
                activityProjector.reprojectForDate(event.deviceId(), date);
            } catch (Exception e) {
                log.error("Failed to reproject activity for date {}: {}", date, e.getMessage(), e);
            }
        });
    }
}
