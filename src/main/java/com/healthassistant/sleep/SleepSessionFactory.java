package com.healthassistant.sleep;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.SleepSessionPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
class SleepSessionFactory {

    Optional<SleepSession> createFromEvent(StoredEventData eventData) {
        if (!(eventData.payload() instanceof SleepSessionPayload payload)) {
            log.warn("Expected SleepSessionPayload but got {}, skipping",
                    eventData.payload().getClass().getSimpleName());
            return Optional.empty();
        }

        if (payload.sleepStart() == null || payload.sleepEnd() == null || payload.totalMinutes() == null) {
            log.warn("SleepSession event missing required fields, skipping");
            return Optional.empty();
        }

        if (payload.totalMinutes() <= 0) {
            log.debug("SleepSession event has zero or negative duration, skipping");
            return Optional.empty();
        }

        return Optional.of(SleepSession.create(
                payload.sleepId(),
                eventData.eventId().value(),
                payload.sleepStart(),
                payload.sleepEnd(),
                payload.totalMinutes(),
                payload.originPackage()
        ));
    }
}
