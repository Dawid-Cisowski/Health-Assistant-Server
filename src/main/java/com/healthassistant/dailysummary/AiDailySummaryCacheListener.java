package com.healthassistant.dailysummary;

import com.healthassistant.healthevents.api.dto.events.AllEventsStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
class AiDailySummaryCacheListener {

    private final DailySummaryJpaRepository repository;

    @ApplicationModuleListener
    public void onAllEventsStored(AllEventsStoredEvent event) {
        if (event.affectedDates().isEmpty()) {
            return;
        }

        log.debug("Updating last_event_at for {} dates due to new events", event.affectedDates().size());
        repository.updateLastEventAtForDates(event.affectedDates(), Instant.now());
        log.debug("Updated last_event_at for dates: {}", event.affectedDates());
    }
}
