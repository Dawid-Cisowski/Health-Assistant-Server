package com.healthassistant.healthevents;

import com.healthassistant.healthevents.api.model.IdempotencyKey;

import java.util.List;
import java.util.Map;

interface EventRepository {

    void saveAll(List<Event> events);

    void updateAll(List<Event> events);

    Map<String, Event> findExistingEventsByIdempotencyKeys(List<IdempotencyKey> idempotencyKeys);
}
