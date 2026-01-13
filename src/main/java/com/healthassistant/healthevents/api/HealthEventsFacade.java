package com.healthassistant.healthevents.api;

import com.healthassistant.healthevents.api.dto.EventData;
import com.healthassistant.healthevents.api.dto.ExistingSleepInfo;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.model.DeviceId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HealthEventsFacade {
    StoreHealthEventsResult storeHealthEvents(StoreHealthEventsCommand command);

    List<EventData> findEventsByOccurredAtBetween(Instant start, Instant end);

    List<EventData> findEventsByDeviceId(String deviceId);

    List<StoredEventData> findEventsForReprojection(int page, int size);

    List<StoredEventData> findEventsForDateRange(String deviceId, Instant start, Instant end);

    long countAllEvents();

    void deleteEventsByDeviceId(String deviceId);

    Optional<ExistingSleepInfo> findExistingSleepInfo(DeviceId deviceId, Instant sleepStart);

    Optional<StoredEventData> findEventById(String deviceId, String eventId);
}
