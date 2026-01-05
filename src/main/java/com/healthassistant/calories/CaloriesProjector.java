package com.healthassistant.calories;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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

        CaloriesDailyProjectionJpaEntity daily = dailyRepository.findByDeviceIdAndDate(deviceId, date)
                .orElseGet(() -> CaloriesDailyProjectionJpaEntity.builder()
                        .deviceId(deviceId)
                        .date(date)
                        .build());

        daily.updateFromHourlyData(hourlyData);
        dailyRepository.save(daily);
    }
}
