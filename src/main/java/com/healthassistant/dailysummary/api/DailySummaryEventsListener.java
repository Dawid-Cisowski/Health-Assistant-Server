package com.healthassistant.dailysummary.api;

import com.healthassistant.healthevents.api.dto.EventsStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
class DailySummaryEventsListener {

    private final DailySummaryFacade dailySummaryFacade;

    @EventListener
    public void onEventsStored(EventsStoredEvent event) {
        log.info("DailySummary listener received EventsStoredEvent for {} affected dates",
                event.affectedDates().size());

        for (LocalDate date : event.affectedDates()) {
            try {
                log.debug("Regenerating daily summary for date: {}", date);
                dailySummaryFacade.generateDailySummary(date);
                log.debug("Successfully regenerated daily summary for date: {}", date);
            } catch (Exception e) {
                log.error("Failed to regenerate daily summary for date: {}", date, e);
            }
        }

        log.info("DailySummary listener completed processing {} dates",
                event.affectedDates().size());
    }
}
