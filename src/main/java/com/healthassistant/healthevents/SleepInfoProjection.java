package com.healthassistant.healthevents;

import java.time.Instant;

interface SleepInfoProjection {
    String getIdempotencyKey();
    String getEventId();
    Instant getSleepStart();
    Instant getSleepEnd();
}
