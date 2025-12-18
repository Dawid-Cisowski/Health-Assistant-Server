package com.healthassistant.activity;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.ActiveMinutesPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
}
