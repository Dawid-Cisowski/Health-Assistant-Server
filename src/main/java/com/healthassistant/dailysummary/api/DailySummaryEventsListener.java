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
        log.info("DailySummary listener received AllEventsStoredEvent for {} affected dates, {} event types",
                event.affectedDates().size(), event.eventTypes().size());

        for (LocalDate date : event.affectedDates()) {
            try {
                log.debug("Regenerating daily summary for date: {}", date);
                dailySummaryFacade.generateDailySummary(date);
                log.debug("Successfully regenerated daily summary for date: {}", date);
            } catch (Exception e) {
                log.error("Failed to regenerate daily summary for date: {}", date, e);
            }
        }

        log.info("DailySummary listener completed processing {} dates", event.affectedDates().size());
    }
}
