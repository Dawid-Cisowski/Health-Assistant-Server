package com.healthassistant.healthevents;

import com.healthassistant.healthevents.api.model.EventId;

interface EventIdGenerator {

    EventId generate();
}
