package com.healthassistant.steps;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.StepsPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
                payload.bucketStart(),
                payload.bucketEnd(),
                payload.count()
        ));
    }
}
