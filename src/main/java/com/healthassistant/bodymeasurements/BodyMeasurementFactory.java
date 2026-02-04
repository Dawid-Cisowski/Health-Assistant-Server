package com.healthassistant.bodymeasurements;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.BodyMeasurementPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
class BodyMeasurementFactory {

    Optional<BodyMeasurement> createFromEvent(StoredEventData eventData) {
        if (!(eventData.payload() instanceof BodyMeasurementPayload payload)) {
            log.warn("Expected BodyMeasurementPayload but got {}, skipping",
                    eventData.payload().getClass().getSimpleName());
            return Optional.empty();
        }

        if (payload.measuredAt() == null) {
            log.warn("BodyMeasurement event missing required field measuredAt, skipping");
            return Optional.empty();
        }

        return Optional.of(BodyMeasurement.create(
                eventData.deviceId().value(),
                eventData.eventId().value(),
                payload.measurementId(),
                payload.measuredAt(),
                payload.bicepsLeftCm(),
                payload.bicepsRightCm(),
                payload.forearmLeftCm(),
                payload.forearmRightCm(),
                payload.chestCm(),
                payload.waistCm(),
                payload.abdomenCm(),
                payload.hipsCm(),
                payload.neckCm(),
                payload.shouldersCm(),
                payload.thighLeftCm(),
                payload.thighRightCm(),
                payload.calfLeftCm(),
                payload.calfRightCm(),
                payload.notes()
        ));
    }
}
