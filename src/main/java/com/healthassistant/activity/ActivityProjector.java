package com.healthassistant.activity;

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
class ActivityProjector {

    private final ActivityDailyProjectionJpaRepository dailyRepository;
    private final ActivityHourlyProjectionJpaRepository hourlyRepository;
    private final ActivityBucketFactory activityBucketFactory;

    @Transactional
    public void projectActivity(StoredEventData eventData) {
        activityBucketFactory.createFromEvent(eventData)
                .ifPresent(this::saveProjection);
    }

    @Transactional
    public void projectCorrectedActivity(String deviceId, Map<String, Object> payload, Instant occurredAt) {
        activityBucketFactory.createFromCorrectionPayload(deviceId, payload)
                .ifPresent(this::saveProjection);
    }

    @Transactional
    public void reprojectForDate(String deviceId, LocalDate date) {
        log.info("Reprojecting activity for device {} on date {}", deviceId, date);
        hourlyRepository.deleteByDeviceIdAndDate(deviceId, date);
        dailyRepository.deleteByDeviceIdAndDate(deviceId, date);
        log.info("Deleted activity projections for device {} on date {} - events will be reprojected on next sync", deviceId, date);
    }

    private void saveProjection(ActivityBucket bucket) {
        try {
            doSaveProjection(bucket);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Version conflict for activity bucket {}/{}/{}, retrying once",
                    bucket.deviceId(), bucket.date(), bucket.hour());
            doSaveProjection(bucket);
        }
    }

    private void doSaveProjection(ActivityBucket bucket) {
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

        daily.updateDailySummary(
            totalMinutes,
            firstActivityTime,
            lastActivityTime,
            activeHoursCount,
            mostActiveHourData != null ? mostActiveHourData.getHour() : null,
            mostActiveHourData != null ? mostActiveHourData.getActiveMinutes() : null
        );

        dailyRepository.save(daily);
    }
}
