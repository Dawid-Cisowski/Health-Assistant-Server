package com.healthassistant.sleep;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.SleepSessionPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    Optional<SleepSession> createFromCorrectionPayload(String deviceId, Map<String, Object> payload) {
        Instant sleepStart = parseInstant(payload.get("sleepStart"));
        Instant sleepEnd = parseInstant(payload.get("sleepEnd"));
        Integer totalMinutes = parseInteger(payload.get("totalMinutes"));

        if (sleepStart == null || sleepEnd == null || totalMinutes == null) {
            log.warn("Corrected SleepSession payload missing required fields, skipping");
            return Optional.empty();
        }

        if (totalMinutes <= 0) {
            log.debug("Corrected SleepSession has zero or negative duration, skipping");
            return Optional.empty();
        }

        SleepStages stages = SleepStages.of(
                parseInteger(payload.get("lightSleepMinutes")),
                parseInteger(payload.get("deepSleepMinutes")),
                parseInteger(payload.get("remSleepMinutes")),
                parseInteger(payload.get("awakeMinutes"))
        );

        Integer sleepScore = parseInteger(payload.get("sleepScore"));
        if (sleepScore == null) {
            sleepScore = sleepScoreCalculator.calculateScore(sleepStart, totalMinutes);
        }

        String sleepId = payload.get("sleepId") != null ? payload.get("sleepId").toString() : UUID.randomUUID().toString();
        String originPackage = payload.get("originPackage") != null ? payload.get("originPackage").toString() : "correction";
        String correctionEventId = "corrected-" + UUID.randomUUID().toString().substring(0, 8);

        return Optional.of(SleepSession.create(
                deviceId,
                sleepId,
                correctionEventId,
                sleepStart,
                sleepEnd,
                totalMinutes,
                stages,
                sleepScore,
                originPackage
        ));
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
