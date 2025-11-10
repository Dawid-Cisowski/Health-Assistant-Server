package com.healthassistant.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates unique event IDs in the format evt_XXXX
 */
@Component
public class EventIdGenerator {

    private static final String PREFIX = "evt_";
    private static final int RANDOM_BYTES = 12; // 96 bits
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public String generate() {
        byte[] randomBytes = new byte[RANDOM_BYTES];
        random.nextBytes(randomBytes);
        return PREFIX + encoder.encodeToString(randomBytes);
    }
}

