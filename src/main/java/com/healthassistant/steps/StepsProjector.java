package com.healthassistant.steps;

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
class StepsProjector {

    private final StepsDailyProjectionJpaRepository dailyRepository;
    private final StepsHourlyProjectionJpaRepository hourlyRepository;
    private final StepsBucketFactory stepsBucketFactory;

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() < 8) return "***";
        return deviceId.substring(0, 4) + "..." + deviceId.substring(deviceId.length() - 4);
    }

    @Transactional
    public void projectSteps(StoredEventData eventData) {
        stepsBucketFactory.createFromEvent(eventData)
                .ifPresent(this::saveProjection);
    }

    @Transactional
    public void projectCorrectedSteps(String deviceId, Map<String, Object> payload, Instant occurredAt) {
        stepsBucketFactory.createFromCorrectionPayload(deviceId, payload)
                .ifPresent(this::saveProjection);
    }

    @Transactional
    public void reprojectForDate(String deviceId, LocalDate date) {
        log.info("Reprojecting steps for device {} on date {}", maskDeviceId(deviceId), date);
        hourlyRepository.deleteByDeviceIdAndDate(deviceId, date);
        dailyRepository.deleteByDeviceIdAndDate(deviceId, date);
        log.info("Deleted projections for device {} on date {} - events will be reprojected on next sync", maskDeviceId(deviceId), date);
    }

    private void saveProjection(StepsBucket bucket) {
        try {
            doSaveProjection(bucket);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Version conflict for steps bucket {}/{}/{}, retrying once",
                    bucket.deviceId(), bucket.date(), bucket.hour());
            doSaveProjection(bucket);
        }
    }

    private void doSaveProjection(StepsBucket bucket) {
        StepsHourlyProjectionJpaEntity hourly = hourlyRepository
                .findByDeviceIdAndDateAndHour(bucket.deviceId(), bucket.date(), bucket.hour())
                .map(existing -> {
                    existing.addBucket(bucket);
                    return existing;
                })
                .orElseGet(() -> StepsHourlyProjectionJpaEntity.from(bucket));

        hourlyRepository.save(hourly);
        updateDailyProjection(bucket.deviceId(), bucket.date());
    }

    private void updateDailyProjection(String deviceId, LocalDate date) {
        List<StepsHourlyProjectionJpaEntity> hourlyData =
                hourlyRepository.findByDeviceIdAndDateOrderByHourAsc(deviceId, date);

        if (hourlyData.isEmpty()) {
            return;
        }

        int totalSteps = hourlyData.stream()
                .mapToInt(StepsHourlyProjectionJpaEntity::getStepCount)
                .sum();

        Instant firstStepTime = hourlyData.stream()
                .map(StepsHourlyProjectionJpaEntity::getFirstBucketTime)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);

        Instant lastStepTime = hourlyData.stream()
                .map(StepsHourlyProjectionJpaEntity::getLastBucketTime)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        StepsHourlyProjectionJpaEntity mostActiveHourData = hourlyData.stream()
                .max(Comparator.comparingInt(StepsHourlyProjectionJpaEntity::getStepCount))
                .orElse(null);

        int activeHoursCount = (int) hourlyData.stream()
                .filter(h -> h.getStepCount() > 0)
                .count();

        StepsDailyProjectionJpaEntity daily = dailyRepository.findByDeviceIdAndDate(deviceId, date)
                .orElseGet(() -> StepsDailyProjectionJpaEntity.builder()
                        .deviceId(deviceId)
                        .date(date)
                        .build());

        daily.updateDailySummary(
                totalSteps,
                firstStepTime,
                lastStepTime,
                activeHoursCount,
                mostActiveHourData != null ? mostActiveHourData.getHour() : null,
                mostActiveHourData != null ? mostActiveHourData.getStepCount() : null
        );

        dailyRepository.save(daily);
    }
}
