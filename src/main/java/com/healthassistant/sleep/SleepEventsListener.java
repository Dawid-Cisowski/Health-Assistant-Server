package com.healthassistant.sleep;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.events.SleepEventsStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class SleepEventsListener {

    private final SleepProjector sleepProjector;

    @ApplicationModuleListener
    public void onSleepEventsStored(SleepEventsStoredEvent event) {
        log.info("Sleep listener received SleepEventsStoredEvent with {} events for {} dates",
                event.events().size(), event.affectedDates().size());

        for (StoredEventData eventData : event.events()) {
            try {
                log.debug("Processing SleepSessionRecorded event: {}", eventData.eventId().value());
                sleepProjector.projectSleep(eventData);
            } catch (Exception e) {
                log.error("Failed to project sleep for event: {}", eventData.eventId().value(), e);
            }
        }

        log.info("Sleep listener completed processing {} events", event.events().size());
    }
}
