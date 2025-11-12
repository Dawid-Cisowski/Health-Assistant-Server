package com.healthassistant.application.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HealthEventsFacade {

    private final StoreHealthEventsCommandHandler commandHandler;

    public StoreHealthEventsResult storeHealthEvents(StoreHealthEventsCommand command) {
        return commandHandler.handle(command);
    }
}
