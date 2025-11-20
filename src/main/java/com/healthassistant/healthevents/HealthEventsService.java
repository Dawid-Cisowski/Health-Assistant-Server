package com.healthassistant.healthevents;

import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.EventsStoredEvent;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
class HealthEventsService implements HealthEventsFacade {

    private final StoreHealthEventsCommandHandler commandHandler;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public StoreHealthEventsResult storeHealthEvents(StoreHealthEventsCommand command) {
        log.debug("Storing {} health events", command.events().size());

        StoreHealthEventsResult result = commandHandler.handle(command);

        if (!result.affectedDates().isEmpty()) {
            log.info("Publishing EventsStoredEvent for {} affected dates, {} event types, and {} stored events",
                    result.affectedDates().size(),
                    result.eventTypes().size(),
                    result.storedEvents().size());

            eventPublisher.publishEvent(
                    new EventsStoredEvent(result.affectedDates(), result.eventTypes(), result.storedEvents())
            );
        }

        return result;
    }
}
