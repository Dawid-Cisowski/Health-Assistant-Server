package com.healthassistant.sleep;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
class SleepProjector {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final String SLEEP_SESSION_V1 = "SleepSessionRecorded.v1";

    private final SleepSessionProjectionJpaRepository sessionRepository;
    private final SleepDailyProjectionJpaRepository dailyRepository;

    @Transactional
    public void projectSleep(StoredEventData eventData) {
        String eventType = eventData.eventType().value();

        if (SLEEP_SESSION_V1.equals(eventType)) {
            try {
                projectSleepSession(eventData);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                log.warn("Race condition during sleep projection for event {}, skipping", eventData.eventId().value());
            }
        }
    }

    private void projectSleepSession(StoredEventData eventData) {
        Map<String, Object> payload = eventData.payload();

        Instant sleepStart = parseInstant(payload.get("sleepStart"));
        Instant sleepEnd = parseInstant(payload.get("sleepEnd"));
        Integer totalMinutes = getInteger(payload, "totalMinutes");
        String originPackage = (String) payload.get("originPackage");

        if (sleepStart == null || sleepEnd == null || totalMinutes == null) {
            log.warn("SleepSession event missing required fields, skipping projection");
            return;
        }

        if (totalMinutes <= 0) {
            log.debug("SleepSession event has zero or negative duration, skipping projection");
            return;
        }

        // Use sleepEnd to determine the date (day when you woke up)
        ZonedDateTime endZoned = sleepEnd.atZone(POLAND_ZONE);
        LocalDate date = endZoned.toLocalDate();

        updateSessionProjection(eventData.eventId().value(), date, sleepStart, sleepEnd, totalMinutes, originPackage);
    }

    private synchronized void updateSessionProjection(
            String eventId,
            LocalDate date,
            Instant sleepStart,
            Instant sleepEnd,
            int durationMinutes,
            String originPackage
    ) {
        Optional<SleepSessionProjectionJpaEntity> existingOpt = sessionRepository.findByEventId(eventId);

        SleepSessionProjectionJpaEntity session;

        if (existingOpt.isPresent()) {
            // Update existing session
            session = existingOpt.get();
            session.setSleepStart(sleepStart);
            session.setSleepEnd(sleepEnd);
            session.setDurationMinutes(durationMinutes);
            session.setOriginPackage(originPackage);
            log.debug("Updating existing sleep session for event {}", eventId);
        } else {
            // Create new session
            List<SleepSessionProjectionJpaEntity> existingSessions =
                sessionRepository.findByDateOrderBySessionNumberAsc(date);

            int sessionNumber = existingSessions.isEmpty() ? 1 :
                existingSessions.get(existingSessions.size() - 1).getSessionNumber() + 1;

            session = SleepSessionProjectionJpaEntity.builder()
                .eventId(eventId)
                .date(date)
                .sessionNumber(sessionNumber)
                .sleepStart(sleepStart)
                .sleepEnd(sleepEnd)
                .durationMinutes(durationMinutes)
                .originPackage(originPackage)
                .build();

            log.debug("Creating new sleep session #{} for date {} from event {}", sessionNumber, date, eventId);
        }

        sessionRepository.save(session);

        updateDailyProjection(date);
    }

    private void updateDailyProjection(LocalDate date) {
        List<SleepSessionProjectionJpaEntity> sessions =
            sessionRepository.findByDateOrderBySessionNumberAsc(date);

        if (sessions.isEmpty()) {
            return;
        }

        int totalSleepMinutes = sessions.stream()
            .mapToInt(SleepSessionProjectionJpaEntity::getDurationMinutes)
            .sum();

        int sleepCount = sessions.size();

        Instant firstSleepStart = sessions.stream()
            .map(SleepSessionProjectionJpaEntity::getSleepStart)
            .filter(Objects::nonNull)
            .min(Instant::compareTo)
            .orElse(null);

        Instant lastSleepEnd = sessions.stream()
            .map(SleepSessionProjectionJpaEntity::getSleepEnd)
            .filter(Objects::nonNull)
            .max(Instant::compareTo)
            .orElse(null);

        Integer longestSessionMinutes = sessions.stream()
            .map(SleepSessionProjectionJpaEntity::getDurationMinutes)
            .filter(Objects::nonNull)
            .max(Integer::compareTo)
            .orElse(null);

        Integer shortestSessionMinutes = sessions.stream()
            .map(SleepSessionProjectionJpaEntity::getDurationMinutes)
            .filter(Objects::nonNull)
            .min(Integer::compareTo)
            .orElse(null);

        int averageSessionMinutes = sleepCount > 0 ? totalSleepMinutes / sleepCount : 0;

        // Future: Aggregate sleep phases
        int totalLightSleep = sessions.stream()
            .mapToInt(s -> s.getLightSleepMinutes() != null ? s.getLightSleepMinutes() : 0)
            .sum();

        int totalDeepSleep = sessions.stream()
            .mapToInt(s -> s.getDeepSleepMinutes() != null ? s.getDeepSleepMinutes() : 0)
            .sum();

        int totalRemSleep = sessions.stream()
            .mapToInt(s -> s.getRemSleepMinutes() != null ? s.getRemSleepMinutes() : 0)
            .sum();

        int totalAwake = sessions.stream()
            .mapToInt(s -> s.getAwakeMinutes() != null ? s.getAwakeMinutes() : 0)
            .sum();

        Optional<SleepDailyProjectionJpaEntity> existingOpt = dailyRepository.findByDate(date);

        SleepDailyProjectionJpaEntity daily;

        daily = existingOpt.orElseGet(() -> SleepDailyProjectionJpaEntity.builder()
                .date(date)
                .build());

        daily.setTotalSleepMinutes(totalSleepMinutes);
        daily.setSleepCount(sleepCount);
        daily.setFirstSleepStart(firstSleepStart);
        daily.setLastSleepEnd(lastSleepEnd);
        daily.setLongestSessionMinutes(longestSessionMinutes);
        daily.setShortestSessionMinutes(shortestSessionMinutes);
        daily.setAverageSessionMinutes(averageSessionMinutes);
        daily.setTotalLightSleepMinutes(totalLightSleep);
        daily.setTotalDeepSleepMinutes(totalDeepSleep);
        daily.setTotalRemSleepMinutes(totalRemSleep);
        daily.setTotalAwakeMinutes(totalAwake);

        dailyRepository.save(daily);
    }

    // Helper methods

    private Instant parseInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant) {
            return (Instant) value;
        }
        if (value instanceof String) {
            try {
                return Instant.parse((String) value);
            } catch (Exception e) {
                log.warn("Failed to parse Instant from string: {}", value);
                return null;
            }
        }
        return null;
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
