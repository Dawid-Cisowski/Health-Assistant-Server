package com.healthassistant.application.ingestion;

import com.healthassistant.domain.event.Event;
import com.healthassistant.domain.event.IdempotencyKey;

import java.util.List;
import java.util.Set;

interface EventRepository {

    void saveAll(List<Event> events);

    Set<String> findExistingIdempotencyKeys(List<IdempotencyKey> idempotencyKeys);
}

