package com.healthassistant.healthevents.api;

import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;

public interface HealthEventsFacade {
    StoreHealthEventsResult storeHealthEvents(StoreHealthEventsCommand command);
}
