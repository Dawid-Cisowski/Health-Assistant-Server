package com.healthassistant.application.ingestion;

import com.healthassistant.domain.event.Event;
import com.healthassistant.domain.event.IdempotencyKey;

interface EventRepository {

    void save(Event event);

    boolean existsByIdempotencyKey(IdempotencyKey idempotencyKey);
}

