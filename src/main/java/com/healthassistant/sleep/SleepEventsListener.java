package com.healthassistant.sleep;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.events.CompensationEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.SleepEventsStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class SleepEventsListener {

    private static final String SLEEP_SESSION_V1 = "SleepSessionRecorded.v1";

    private final SleepProjector sleepProjector;

    @ApplicationModuleListener
    public void onSleepEventsStored(SleepEventsStoredEvent event) {
        log.info("Sleep listener received SleepEventsStoredEvent with {} events for {} dates",
                event.events().size(), event.affectedDates().size());

        event.events().forEach(eventData -> {
            try {
                log.debug("Processing SleepSessionRecorded event: {}", eventData.eventId().value());
                sleepProjector.projectSleep(eventData);
            } catch (Exception e) {
                log.error("Failed to project sleep for event: {}", eventData.eventId().value(), e);
            }
        });

        log.info("Sleep listener completed processing {} events", event.events().size());
    }

    @ApplicationModuleListener
    public void onCompensationEventsStored(CompensationEventsStoredEvent event) {
        var sleepDeletions = event.deletions().stream()
                .filter(d -> SLEEP_SESSION_V1.equals(d.targetEventType()))
                .toList();

        var sleepCorrections = event.corrections().stream()
                .filter(c -> SLEEP_SESSION_V1.equals(c.targetEventType()) || SLEEP_SESSION_V1.equals(c.correctedEventType()))
                .toList();

        if (sleepDeletions.isEmpty() && sleepCorrections.isEmpty()) {
            return;
        }

        log.info("Sleep listener processing {} deletions and {} corrections",
                sleepDeletions.size(), sleepCorrections.size());

        sleepDeletions.forEach(deletion -> {
            try {
                sleepProjector.deleteByEventId(deletion.targetEventId());
            } catch (Exception e) {
                log.error("Failed to delete sleep projection for eventId: {}", deletion.targetEventId(), e);
            }
        });

        sleepCorrections.forEach(correction -> {
            try {
                sleepProjector.deleteByEventId(correction.targetEventId());
                if (SLEEP_SESSION_V1.equals(correction.correctedEventType()) && correction.correctedPayload() != null) {
                    sleepProjector.projectCorrectedSleep(
                            event.deviceId(),
                            correction.correctedPayload()
                    );
                }
            } catch (Exception e) {
                log.error("Failed to process correction for sleep eventId: {}", correction.targetEventId(), e);
            }
        });
    }
}
