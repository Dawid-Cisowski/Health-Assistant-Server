package com.healthassistant.healthevents;

import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

interface EventRepository {

    void saveAll(List<Event> events);

    void updateAll(List<Event> events);

    Map<String, Event> findExistingEventsByIdempotencyKeys(List<IdempotencyKey> idempotencyKeys);

    Optional<IdempotencyKey> findSleepIdempotencyKeyByDeviceIdAndSleepStart(DeviceId deviceId, Instant sleepStart);
}
