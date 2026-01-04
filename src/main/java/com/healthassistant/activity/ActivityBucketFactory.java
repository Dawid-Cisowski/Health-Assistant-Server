package com.healthassistant.activity;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.ActiveMinutesPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
class ActivityBucketFactory {

    Optional<ActivityBucket> createFromEvent(StoredEventData eventData) {
        if (!(eventData.payload() instanceof ActiveMinutesPayload payload)) {
            log.warn("Expected ActiveMinutesPayload but got {}, skipping",
                    eventData.payload().getClass().getSimpleName());
            return Optional.empty();
        }

        if (payload.bucketStart() == null || payload.bucketEnd() == null) {
            log.warn("ActiveMinutesRecorded event missing bucketStart or bucketEnd, skipping");
            return Optional.empty();
        }

        if (payload.activeMinutes() == null || payload.activeMinutes() <= 0) {
            log.debug("ActiveMinutesRecorded event has zero or negative minutes, skipping");
            return Optional.empty();
        }

        return Optional.of(ActivityBucket.create(
                eventData.deviceId().value(),
                payload.bucketStart(),
                payload.bucketEnd(),
                payload.activeMinutes()
        ));
    }

    Optional<ActivityBucket> createFromCorrectionPayload(String deviceId, Map<String, Object> payload) {
        Instant bucketStart = parseInstant(payload.get("bucketStart"));
        Instant bucketEnd = parseInstant(payload.get("bucketEnd"));
        Integer activeMinutes = parseInteger(payload.get("activeMinutes"));

        if (bucketStart == null || bucketEnd == null) {
            log.warn("Corrected activity payload missing bucketStart or bucketEnd, skipping");
            return Optional.empty();
        }

        if (activeMinutes == null || activeMinutes <= 0) {
            log.debug("Corrected activity has zero or negative minutes, skipping");
            return Optional.empty();
        }

        return Optional.of(ActivityBucket.create(deviceId, bucketStart, bucketEnd, activeMinutes));
    }

    private Instant parseInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        return Instant.parse(value.toString());
    }

    private Integer parseInteger(Object value) {
        return switch (value) {
            case null -> null;
            case Integer i -> i;
            case Number n -> n.intValue();
            default -> Integer.parseInt(value.toString());
        };
    }
}
