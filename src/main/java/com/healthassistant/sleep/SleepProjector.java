package com.healthassistant.sleep;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
class SleepProjector {

    private final SleepSessionProjectionJpaRepository sessionRepository;
    private final SleepDailyProjectionJpaRepository dailyRepository;
    private final SleepSessionFactory sleepSessionFactory;

    public void projectSleep(StoredEventData eventData) {
        sleepSessionFactory.createFromEvent(eventData)
                .ifPresent(this::saveProjection);
    }

    private void saveProjection(SleepSession session) {
        try {
            doSaveProjection(session);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Version conflict for sleep session {}/{}, retrying once",
                    maskDeviceId(session.deviceId()), session.date());
            doSaveProjection(session);
        }
    }

    private void doSaveProjection(SleepSession session) {
        Optional<SleepSessionProjectionJpaEntity> existingOpt = sessionRepository.findByEventId(session.eventId());

        SleepSessionProjectionJpaEntity entity;

        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            entity.updateFrom(session);
            log.debug("Updating existing sleep session for event {}", session.eventId());
        } else {
            int sessionNumber = calculateNextSessionNumber(session.deviceId(), session.date());
            entity = SleepSessionProjectionJpaEntity.from(session, sessionNumber);
            log.debug("Creating new sleep session #{} for date {} from event {}",
                    sessionNumber, session.date(), session.eventId());
        }

        sessionRepository.save(entity);
        updateDailyProjection(session.deviceId(), session.date());
    }

    private int calculateNextSessionNumber(String deviceId, LocalDate date) {
        List<SleepSessionProjectionJpaEntity> existingSessions =
                sessionRepository.findByDeviceIdAndDateOrderBySessionNumberAsc(deviceId, date);
        return existingSessions.isEmpty() ? 1 :
                existingSessions.getLast().getSessionNumber() + 1;
    }

    public void deleteByEventId(String eventId) {
        sessionRepository.findByEventId(eventId).ifPresent(entity -> {
            String deviceId = entity.getDeviceId();
            LocalDate date = entity.getDate();
            sessionRepository.deleteByEventId(eventId);
            log.info("Deleted sleep projection for eventId: {}", eventId);
            updateDailyProjection(deviceId, date);
        });
    }

    public void projectCorrectedSleep(String deviceId, java.util.Map<String, Object> payload, java.time.Instant occurredAt) {
        sleepSessionFactory.createFromCorrectionPayload(deviceId, payload, occurredAt)
                .ifPresent(this::saveProjection);
    }

    private void updateDailyProjection(String deviceId, LocalDate date) {
        List<SleepSessionProjectionJpaEntity> sessions =
                sessionRepository.findByDeviceIdAndDateOrderBySessionNumberAsc(deviceId, date);

        if (sessions.isEmpty()) {
            dailyRepository.findByDeviceIdAndDate(deviceId, date)
                    .ifPresent(daily -> {
                        dailyRepository.delete(daily);
                        log.debug("Deleted empty daily sleep projection for {}/{}", maskDeviceId(deviceId), date);
                    });
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

        List<Integer> scores = sessions.stream()
                .map(SleepSessionProjectionJpaEntity::getSleepScore)
                .filter(Objects::nonNull)
                .toList();
        Integer averageSleepScore = scores.isEmpty() ? null :
                (int) scores.stream().mapToInt(Integer::intValue).average().orElse(0);

        SleepDailyProjectionJpaEntity daily = dailyRepository.findByDeviceIdAndDate(deviceId, date)
                .orElseGet(() -> SleepDailyProjectionJpaEntity.builder()
                        .deviceId(deviceId)
                        .date(date)
                        .build());

        daily.updateDailyStats(
                totalSleepMinutes,
                sleepCount,
                firstSleepStart,
                lastSleepEnd,
                longestSessionMinutes,
                shortestSessionMinutes,
                averageSessionMinutes,
                totalLightSleep,
                totalDeepSleep,
                totalRemSleep,
                totalAwake,
                averageSleepScore
        );

        dailyRepository.save(daily);
    }

    private String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() < 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "..." + deviceId.substring(deviceId.length() - 4);
    }
}
