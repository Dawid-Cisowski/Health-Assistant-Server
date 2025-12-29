package com.healthassistant.sleep;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.SleepSessionPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
class SleepSessionFactory {

    private final SleepScoreCalculator sleepScoreCalculator;

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

        SleepStages stages = SleepStages.of(
                payload.lightSleepMinutes(),
                payload.deepSleepMinutes(),
                payload.remSleepMinutes(),
                payload.awakeMinutes()
        );

        Integer sleepScore = payload.sleepScore();
        if (sleepScore == null) {
            sleepScore = sleepScoreCalculator.calculateScore(
                    payload.sleepStart(),
                    payload.sleepEnd(),
                    payload.totalMinutes()
            );
            log.debug("Auto-calculated sleep score {} for session {}", sleepScore, payload.sleepId());
        }

        return Optional.of(SleepSession.create(
                eventData.deviceId().value(),
                payload.sleepId(),
                eventData.eventId().value(),
                payload.sleepStart(),
                payload.sleepEnd(),
                payload.totalMinutes(),
                stages,
                sleepScore,
                payload.originPackage()
        ));
    }
}
