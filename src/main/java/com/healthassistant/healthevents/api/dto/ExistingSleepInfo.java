package com.healthassistant.healthevents.api.dto;

import com.healthassistant.healthevents.api.model.EventId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;

import java.time.Instant;

public record ExistingSleepInfo(
        IdempotencyKey idempotencyKey,
        EventId eventId,
        Instant sleepStart,
        Instant sleepEnd
) {}
