package com.healthassistant.application.ingestion;

import com.healthassistant.domain.event.Event;
import com.healthassistant.domain.event.IdempotencyKey;

import java.util.List;
import java.util.Map;
import java.util.Set;

interface EventRepository {

    void saveAll(List<Event> events);

    void updateAll(List<Event> events);

    Map<String, Event> findExistingEventsByIdempotencyKeys(List<IdempotencyKey> idempotencyKeys);
}

