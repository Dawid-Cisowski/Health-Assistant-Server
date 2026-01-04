package com.healthassistant.steps;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.StepsPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
class StepsBucketFactory {

    Optional<StepsBucket> createFromEvent(StoredEventData eventData) {
        if (!(eventData.payload() instanceof StepsPayload payload)) {
            log.warn("Expected StepsPayload but got {}, skipping",
                    eventData.payload().getClass().getSimpleName());
            return Optional.empty();
        }

        if (payload.bucketStart() == null || payload.bucketEnd() == null) {
            log.warn("StepsBucketed event missing bucketStart or bucketEnd, skipping");
            return Optional.empty();
        }

        if (payload.count() == null || payload.count() == 0) {
            log.debug("StepsBucketed event has zero steps, skipping");
            return Optional.empty();
        }

        return Optional.of(StepsBucket.create(
                eventData.deviceId().value(),
                payload.bucketStart(),
                payload.bucketEnd(),
                payload.count()
        ));
    }

    Optional<StepsBucket> createFromCorrectionPayload(String deviceId, Map<String, Object> payload) {
        Instant bucketStart = parseInstant(payload.get("bucketStart"));
        Instant bucketEnd = parseInstant(payload.get("bucketEnd"));
        Integer count = parseInteger(payload.get("count"));

        if (bucketStart == null || bucketEnd == null) {
            log.warn("Corrected steps payload missing bucketStart or bucketEnd, skipping");
            return Optional.empty();
        }

        if (count == null || count == 0) {
            log.debug("Corrected steps has zero steps, skipping");
            return Optional.empty();
        }

        return Optional.of(StepsBucket.create(deviceId, bucketStart, bucketEnd, count));
    }

    private Instant parseInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        return Instant.parse(value.toString());
    }

    private Integer parseInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer i) return i;
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(value.toString());
    }
}
