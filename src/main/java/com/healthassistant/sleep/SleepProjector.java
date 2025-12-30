package com.healthassistant.sleep;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
class SleepProjector {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 50;

    private final SleepSessionProjectionJpaRepository sessionRepository;
    private final SleepDailyProjectionJpaRepository dailyRepository;
    private final SleepSessionFactory sleepSessionFactory;
    private final TransactionTemplate transactionTemplate;

    SleepProjector(SleepSessionProjectionJpaRepository sessionRepository,
                   SleepDailyProjectionJpaRepository dailyRepository,
                   SleepSessionFactory sleepSessionFactory,
                   PlatformTransactionManager transactionManager) {
        this.sessionRepository = sessionRepository;
        this.dailyRepository = dailyRepository;
        this.sleepSessionFactory = sleepSessionFactory;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void projectSleep(StoredEventData eventData) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    try {
                        sleepSessionFactory.createFromEvent(eventData)
                                .ifPresent(this::saveProjection);
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        log.warn("Race condition during sleep projection for event {}, skipping", eventData.eventId().value());
                    }
                });
                return;
            } catch (DeadlockLoserDataAccessException | CannotAcquireLockException e) {
                if (attempt == MAX_RETRIES) {
                    log.error("Deadlock persists after {} retries for sleep event {}, giving up",
                            MAX_RETRIES, eventData.eventId().value());
                    throw e;
                }
                long delay = BASE_DELAY_MS * attempt + ThreadLocalRandom.current().nextLong(50);
                log.warn("Deadlock detected for sleep event {} (attempt {}/{}), retrying in {}ms",
                        eventData.eventId().value(), attempt, MAX_RETRIES, delay);
                sleep(delay);
            }
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void saveProjection(SleepSession session) {
        String lockKey = session.deviceId() + "|" + session.date();
        synchronized (lockKey.intern()) {
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
    }

    private int calculateNextSessionNumber(String deviceId, LocalDate date) {
        List<SleepSessionProjectionJpaEntity> existingSessions =
                sessionRepository.findByDeviceIdAndDateOrderBySessionNumberAsc(deviceId, date);
        return existingSessions.isEmpty() ? 1 :
                existingSessions.getLast().getSessionNumber() + 1;
    }

    private void updateDailyProjection(String deviceId, LocalDate date) {
        List<SleepSessionProjectionJpaEntity> sessions =
                sessionRepository.findByDeviceIdAndDateOrderBySessionNumberAsc(deviceId, date);

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
        daily.setAverageSleepScore(averageSleepScore);

        dailyRepository.save(daily);
    }
}
