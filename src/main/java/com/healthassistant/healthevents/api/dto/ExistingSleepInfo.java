package com.healthassistant.healthevents.api.dto;

import com.healthassistant.healthevents.api.model.EventId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;

public record ExistingSleepInfo(
        IdempotencyKey idempotencyKey,
        EventId eventId
) {}
