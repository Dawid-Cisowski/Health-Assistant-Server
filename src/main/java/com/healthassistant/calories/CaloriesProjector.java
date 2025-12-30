package com.healthassistant.calories;

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
class CaloriesProjector {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 50;

    private final CaloriesDailyProjectionJpaRepository dailyRepository;
    private final CaloriesHourlyProjectionJpaRepository hourlyRepository;
    private final CaloriesBucketFactory caloriesBucketFactory;
    private final TransactionTemplate transactionTemplate;

    CaloriesProjector(CaloriesDailyProjectionJpaRepository dailyRepository,
                      CaloriesHourlyProjectionJpaRepository hourlyRepository,
                      CaloriesBucketFactory caloriesBucketFactory,
                      PlatformTransactionManager transactionManager) {
        this.dailyRepository = dailyRepository;
        this.hourlyRepository = hourlyRepository;
        this.caloriesBucketFactory = caloriesBucketFactory;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void projectCalories(StoredEventData eventData) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    try {
                        caloriesBucketFactory.createFromEvent(eventData)
                                .ifPresent(this::saveProjection);
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        log.warn("Race condition during calories projection for event {}, skipping", eventData.eventId().value());
                    }
                });
                return;
            } catch (DeadlockLoserDataAccessException | CannotAcquireLockException e) {
                if (attempt == MAX_RETRIES) {
                    log.error("Deadlock persists after {} retries for calories event {}, giving up",
                            MAX_RETRIES, eventData.eventId().value());
                    throw e;
                }
                long delay = BASE_DELAY_MS * attempt + ThreadLocalRandom.current().nextLong(50);
                log.warn("Deadlock detected for calories event {} (attempt {}/{}), retrying in {}ms",
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

    private synchronized void saveProjection(CaloriesBucket bucket) {
        CaloriesHourlyProjectionJpaEntity hourly = hourlyRepository
                .findByDeviceIdAndDateAndHour(bucket.deviceId(), bucket.date(), bucket.hour())
                .map(existing -> {
                    existing.addBucket(bucket);
                    return existing;
                })
                .orElseGet(() -> CaloriesHourlyProjectionJpaEntity.from(bucket));

        hourlyRepository.save(hourly);
        updateDailyProjection(bucket.deviceId(), bucket.date());
    }

    private void updateDailyProjection(String deviceId, LocalDate date) {
        List<CaloriesHourlyProjectionJpaEntity> hourlyData =
                hourlyRepository.findByDeviceIdAndDateOrderByHourAsc(deviceId, date);

        if (hourlyData.isEmpty()) {
            return;
        }

        double totalCalories = hourlyData.stream()
                .mapToDouble(CaloriesHourlyProjectionJpaEntity::getCaloriesKcal)
                .sum();

        Instant firstCalorieTime = hourlyData.stream()
                .map(CaloriesHourlyProjectionJpaEntity::getFirstBucketTime)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);

        Instant lastCalorieTime = hourlyData.stream()
                .map(CaloriesHourlyProjectionJpaEntity::getLastBucketTime)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        CaloriesHourlyProjectionJpaEntity mostActiveHourData = hourlyData.stream()
                .max(Comparator.comparingDouble(CaloriesHourlyProjectionJpaEntity::getCaloriesKcal))
                .orElse(null);

        int activeHoursCount = (int) hourlyData.stream()
                .filter(h -> h.getCaloriesKcal() > 0)
                .count();

        CaloriesDailyProjectionJpaEntity daily = dailyRepository.findByDeviceIdAndDate(deviceId, date)
                .orElseGet(() -> CaloriesDailyProjectionJpaEntity.builder()
                        .deviceId(deviceId)
                        .date(date)
                        .build());

        daily.setTotalCaloriesKcal(totalCalories);
        daily.setFirstCalorieTime(firstCalorieTime);
        daily.setLastCalorieTime(lastCalorieTime);
        daily.setActiveHoursCount(activeHoursCount);

        if (mostActiveHourData != null) {
            daily.setMostActiveHour(mostActiveHourData.getHour());
            daily.setMostActiveHourCalories(mostActiveHourData.getCaloriesKcal());
        }

        dailyRepository.save(daily);
    }
}
