package com.healthassistant.application.ingestion;

import com.healthassistant.domain.event.EventId;

interface EventIdGenerator {

    EventId generate();
}

