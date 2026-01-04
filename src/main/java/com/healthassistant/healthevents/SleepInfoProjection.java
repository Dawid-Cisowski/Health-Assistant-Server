package com.healthassistant.healthevents;

interface SleepInfoProjection {
    String getIdempotencyKey();
    String getEventId();
}
