package com.healthassistant.calories;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.ActiveCaloriesPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
class CaloriesBucketFactory {

    Optional<CaloriesBucket> createFromEvent(StoredEventData eventData) {
        if (!(eventData.payload() instanceof ActiveCaloriesPayload payload)) {
            log.warn("Expected ActiveCaloriesPayload but got unexpected payload type for event {}, skipping",
                    eventData.eventId().value());
            return Optional.empty();
        }

        if (payload.bucketStart() == null || payload.bucketEnd() == null) {
            log.warn("ActiveCaloriesBurned event missing bucketStart or bucketEnd, skipping");
            return Optional.empty();
        }

        if (payload.energyKcal() == null || payload.energyKcal() <= 0) {
            log.debug("ActiveCaloriesBurned event has zero or negative calories, skipping");
            return Optional.empty();
        }

        return Optional.of(CaloriesBucket.create(
                eventData.deviceId().value(),
                payload.bucketStart(),
                payload.bucketEnd(),
                payload.energyKcal()
        ));
    }

    Optional<CaloriesBucket> createFromCorrectionPayload(String deviceId, Map<String, Object> payload) {
        Instant bucketStart = parseInstant(payload.get("bucketStart"));
        Instant bucketEnd = parseInstant(payload.get("bucketEnd"));
        Double energyKcal = parseDouble(payload.get("energyKcal"));

        if (bucketStart == null || bucketEnd == null) {
            log.warn("Corrected calories payload missing bucketStart or bucketEnd, skipping");
            return Optional.empty();
        }

        if (energyKcal == null || energyKcal <= 0) {
            log.debug("Corrected calories has zero or negative energy, skipping");
            return Optional.empty();
        }

        return Optional.of(CaloriesBucket.create(deviceId, bucketStart, bucketEnd, energyKcal));
    }

    private Instant parseInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        try {
            return Instant.parse(value.toString());
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse instant from value '{}', returning null", value);
            return null;
        }
    }

    private Double parseDouble(Object value) {
        return switch (value) {
            case null -> null;
            case Double d -> d;
            case Number n -> n.doubleValue();
            default -> {
                try {
                    yield Double.parseDouble(value.toString());
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse double from value '{}', returning null", value);
                    yield null;
                }
            }
        };
    }
}
