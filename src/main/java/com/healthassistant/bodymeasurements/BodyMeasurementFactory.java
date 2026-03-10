package com.healthassistant.bodymeasurements;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.BodyMeasurementPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
class BodyMeasurementFactory {

    private final ObjectMapper objectMapper;

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

    Optional<BodyMeasurement> createFromCorrectionPayload(String deviceId, Map<String, Object> payload, Instant occurredAt) {
        try {
            BodyMeasurementPayload bodyPayload = objectMapper.convertValue(payload, BodyMeasurementPayload.class);

            Instant measuredAt = bodyPayload.measuredAt() != null ? bodyPayload.measuredAt() : occurredAt;
            if (measuredAt == null) {
                log.warn("Corrected BodyMeasurement payload missing measuredAt and occurredAt, skipping");
                return Optional.empty();
            }

            String measurementId = bodyPayload.measurementId() != null
                    ? bodyPayload.measurementId()
                    : "corrected-" + UUID.randomUUID();

            String correctionEventId = "corrected-" + UUID.randomUUID();

            return Optional.of(BodyMeasurement.create(
                    deviceId,
                    correctionEventId,
                    measurementId,
                    measuredAt,
                    bodyPayload.bicepsLeftCm(),
                    bodyPayload.bicepsRightCm(),
                    bodyPayload.forearmLeftCm(),
                    bodyPayload.forearmRightCm(),
                    bodyPayload.chestCm(),
                    bodyPayload.waistCm(),
                    bodyPayload.abdomenCm(),
                    bodyPayload.hipsCm(),
                    bodyPayload.neckCm(),
                    bodyPayload.shouldersCm(),
                    bodyPayload.thighLeftCm(),
                    bodyPayload.thighRightCm(),
                    bodyPayload.calfLeftCm(),
                    bodyPayload.calfRightCm(),
                    bodyPayload.notes()
            ));
        } catch (Exception e) {
            log.warn("Failed to create BodyMeasurement from correction payload: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
