package com.healthassistant.calories;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
class CaloriesProjector {

    private final CaloriesDailyProjectionJpaRepository dailyRepository;
    private final CaloriesHourlyProjectionJpaRepository hourlyRepository;
    private final CaloriesBucketFactory caloriesBucketFactory;

    public void projectCalories(StoredEventData eventData) {
        caloriesBucketFactory.createFromEvent(eventData)
                .ifPresent(this::saveProjection);
    }

    public void projectCorrectedCalories(String deviceId, Map<String, Object> payload, Instant occurredAt) {
        caloriesBucketFactory.createFromCorrectionPayload(deviceId, payload)
                .ifPresent(this::saveProjection);
    }

    @Transactional
    public void reprojectForDate(String deviceId, LocalDate date) {
        log.info("Reprojecting calories for device {} on date {}", deviceId, date);
        hourlyRepository.deleteByDeviceIdAndDate(deviceId, date);
        dailyRepository.deleteByDeviceIdAndDate(deviceId, date);
        log.info("Deleted calories projections for device {} on date {} - events will be reprojected on next sync", deviceId, date);
    }

    private void saveProjection(CaloriesBucket bucket) {
        try {
            doSaveProjection(bucket);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Version conflict for calories bucket {}/{}/{}, retrying once",
                    bucket.deviceId(), bucket.date(), bucket.hour());
            doSaveProjection(bucket);
        }
    }

    private void doSaveProjection(CaloriesBucket bucket) {
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
