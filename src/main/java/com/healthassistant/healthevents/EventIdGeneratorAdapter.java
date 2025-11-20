package com.healthassistant.healthevents;

import com.healthassistant.healthevents.api.model.EventId;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
class EventIdGeneratorAdapter implements EventIdGenerator {

    private static final String PREFIX = "evt_";
    private static final int RANDOM_BYTES = 12;
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    @Override
    public EventId generate() {
        byte[] randomBytes = new byte[RANDOM_BYTES];
        random.nextBytes(randomBytes);
        return EventId.of(PREFIX + encoder.encodeToString(randomBytes));
    }
}
