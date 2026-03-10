package com.healthassistant.bodymeasurements;

import java.io.Serial;

final class BodyMeasurementNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("PMD.UnusedFormalParameter")
    BodyMeasurementNotFoundException(String eventId) {
        super("Body measurement not found");
    }
}
