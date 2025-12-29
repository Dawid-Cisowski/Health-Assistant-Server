package com.healthassistant.dailysummary.api;

import com.healthassistant.healthevents.api.dto.events.AllEventsStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
class DailySummaryEventsListener {

    private final DailySummaryFacade dailySummaryFacade;

    @ApplicationModuleListener
    public void onAllEventsStored(AllEventsStoredEvent event) {
        log.info("DailySummary listener received AllEventsStoredEvent for device {} with {} affected dates, {} event types",
                event.deviceId(), event.affectedDates().size(), event.eventTypes().size());

        for (LocalDate date : event.affectedDates()) {
            try {
                log.debug("Regenerating daily summary for device {} date: {}", event.deviceId(), date);
                dailySummaryFacade.generateDailySummary(event.deviceId(), date);
                log.debug("Successfully regenerated daily summary for device {} date: {}", event.deviceId(), date);
            } catch (Exception e) {
                log.error("Failed to regenerate daily summary for device {} date: {}", event.deviceId(), date, e);
            }
        }

        log.info("DailySummary listener completed processing {} dates for device {}", event.affectedDates().size(), event.deviceId());
    }
}
