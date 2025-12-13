package com.healthassistant.sleep;

import com.healthassistant.healthevents.api.dto.EventsStoredEvent;
import com.healthassistant.healthevents.api.dto.StoredEventData;
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
    public void onEventsStored(EventsStoredEvent event) {
        log.info("Sleep listener received EventsStoredEvent with {} events", event.events().size());

        for (StoredEventData eventData : event.events()) {
            String eventType = eventData.eventType().value();
            if ("SleepSessionRecorded.v1".equals(eventType)) {
                try {
                    log.debug("Processing SleepSessionRecorded event: {}", eventData.eventId().value());
                    sleepProjector.projectSleep(eventData);
                } catch (Exception e) {
                    log.error("Failed to project sleep for event: {}", eventData.eventId().value(), e);
                }
            }
        }

        log.info("Sleep listener completed processing {} events", event.events().size());
    }
}
