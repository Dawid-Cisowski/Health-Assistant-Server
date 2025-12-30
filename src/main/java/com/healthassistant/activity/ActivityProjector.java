package com.healthassistant.activity;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
class ActivityProjector {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 50;

    private final ActivityDailyProjectionJpaRepository dailyRepository;
    private final ActivityHourlyProjectionJpaRepository hourlyRepository;
    private final ActivityBucketFactory activityBucketFactory;
    private final TransactionTemplate transactionTemplate;

    ActivityProjector(ActivityDailyProjectionJpaRepository dailyRepository,
                      ActivityHourlyProjectionJpaRepository hourlyRepository,
                      ActivityBucketFactory activityBucketFactory,
                      PlatformTransactionManager transactionManager) {
        this.dailyRepository = dailyRepository;
        this.hourlyRepository = hourlyRepository;
        this.activityBucketFactory = activityBucketFactory;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void projectActivity(StoredEventData eventData) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    try {
                        activityBucketFactory.createFromEvent(eventData)
                                .ifPresent(this::saveProjection);
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        log.warn("Race condition during activity projection for event {}, skipping", eventData.eventId().value());
                    }
                });
                return;
            } catch (DeadlockLoserDataAccessException | CannotAcquireLockException e) {
                if (attempt == MAX_RETRIES) {
                    log.error("Deadlock persists after {} retries for activity event {}, giving up",
                            MAX_RETRIES, eventData.eventId().value());
                    throw e;
                }
                long delay = BASE_DELAY_MS * attempt + ThreadLocalRandom.current().nextLong(50);
                log.warn("Deadlock detected for activity event {} (attempt {}/{}), retrying in {}ms",
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

    private synchronized void saveProjection(ActivityBucket bucket) {
        ActivityHourlyProjectionJpaEntity hourly = hourlyRepository
                .findByDeviceIdAndDateAndHour(bucket.deviceId(), bucket.date(), bucket.hour())
                .map(existing -> {
                    existing.addBucket(bucket);
                    return existing;
                })
                .orElseGet(() -> ActivityHourlyProjectionJpaEntity.from(bucket));

        hourlyRepository.save(hourly);
        updateDailyProjection(bucket.deviceId(), bucket.date());
    }

    private void updateDailyProjection(String deviceId, LocalDate date) {
        List<ActivityHourlyProjectionJpaEntity> hourlyData =
                hourlyRepository.findByDeviceIdAndDateOrderByHourAsc(deviceId, date);

        if (hourlyData.isEmpty()) {
            return;
        }

        int totalMinutes = hourlyData.stream()
                .mapToInt(ActivityHourlyProjectionJpaEntity::getActiveMinutes)
                .sum();

        Instant firstActivityTime = hourlyData.stream()
                .map(ActivityHourlyProjectionJpaEntity::getFirstBucketTime)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);

        Instant lastActivityTime = hourlyData.stream()
                .map(ActivityHourlyProjectionJpaEntity::getLastBucketTime)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        ActivityHourlyProjectionJpaEntity mostActiveHourData = hourlyData.stream()
                .max(Comparator.comparingInt(ActivityHourlyProjectionJpaEntity::getActiveMinutes))
                .orElse(null);

        int activeHoursCount = (int) hourlyData.stream()
                .filter(h -> h.getActiveMinutes() > 0)
                .count();

        ActivityDailyProjectionJpaEntity daily = dailyRepository.findByDeviceIdAndDate(deviceId, date)
                .orElseGet(() -> ActivityDailyProjectionJpaEntity.builder()
                        .deviceId(deviceId)
                        .date(date)
                        .build());

        daily.setTotalActiveMinutes(totalMinutes);
        daily.setFirstActivityTime(firstActivityTime);
        daily.setLastActivityTime(lastActivityTime);
        daily.setActiveHoursCount(activeHoursCount);

        if (mostActiveHourData != null) {
            daily.setMostActiveHour(mostActiveHourData.getHour());
            daily.setMostActiveHourMinutes(mostActiveHourData.getActiveMinutes());
        }

        dailyRepository.save(daily);
    }
}
