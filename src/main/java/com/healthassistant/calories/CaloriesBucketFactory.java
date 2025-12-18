package com.healthassistant.calories;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.ActiveCaloriesPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
class CaloriesBucketFactory {

    Optional<CaloriesBucket> createFromEvent(StoredEventData eventData) {
        if (!(eventData.payload() instanceof ActiveCaloriesPayload payload)) {
            log.warn("Expected ActiveCaloriesPayload but got {}, skipping",
                    eventData.payload().getClass().getSimpleName());
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
}
