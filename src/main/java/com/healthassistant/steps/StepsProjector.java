package com.healthassistant.steps;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
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
class StepsProjector {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final String STEPS_BUCKETED_V1 = "StepsBucketedRecorded.v1";

    private final StepsDailyProjectionJpaRepository dailyRepository;
    private final StepsHourlyProjectionJpaRepository hourlyRepository;
    private final EntityManager entityManager;

    @Transactional
    public void projectSteps(StoredEventData eventData) {
        String eventType = eventData.eventType().value();

        if (STEPS_BUCKETED_V1.equals(eventType)) {
            try {
                projectStepsBucketed(eventData);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                log.warn("Race condition during steps projection for event {}, skipping", eventData.eventId().value());
            }
        }
    }

    private void projectStepsBucketed(StoredEventData eventData) {
        Map<String, Object> payload = eventData.payload();

        Instant bucketStart = parseInstant(payload.get("bucketStart"));
        Instant bucketEnd = parseInstant(payload.get("bucketEnd"));
        Integer stepCount = getInteger(payload, "count", 0);

        if (bucketStart == null || bucketEnd == null) {
            log.warn("StepsBucketed event missing bucketStart or bucketEnd, skipping projection");
            return;
        }

        if (stepCount == 0) {
            log.debug("StepsBucketed event has zero steps, skipping projection");
            return;
        }

        ZonedDateTime startZoned = bucketStart.atZone(POLAND_ZONE);
        LocalDate date = startZoned.toLocalDate();
        int hour = startZoned.getHour();

        updateHourlyProjection(date, hour, stepCount, bucketStart, bucketEnd);
    }

    private synchronized void updateHourlyProjection(
            LocalDate date,
            int hour,
            int stepCount,
            Instant bucketStart,
            Instant bucketEnd
    ) {
        Optional<StepsHourlyProjectionJpaEntity> existingOpt =
            hourlyRepository.findByDateAndHour(date, hour);

        StepsHourlyProjectionJpaEntity hourly;

        if (existingOpt.isPresent()) {
            hourly = existingOpt.get();
            hourly.setStepCount(hourly.getStepCount() + stepCount);
            hourly.setBucketCount(hourly.getBucketCount() + 1);

            if (hourly.getFirstBucketTime() == null || bucketStart.isBefore(hourly.getFirstBucketTime())) {
                hourly.setFirstBucketTime(bucketStart);
            }
            if (hourly.getLastBucketTime() == null || bucketEnd.isAfter(hourly.getLastBucketTime())) {
                hourly.setLastBucketTime(bucketEnd);
            }
        } else {
            hourly = StepsHourlyProjectionJpaEntity.builder()
                .date(date)
                .hour(hour)
                .stepCount(stepCount)
                .bucketCount(1)
                .firstBucketTime(bucketStart)
                .lastBucketTime(bucketEnd)
                .build();
        }

        hourlyRepository.save(hourly);

        updateDailyProjection(date);
    }

    private void updateDailyProjection(LocalDate date) {
        List<StepsHourlyProjectionJpaEntity> hourlyData =
            hourlyRepository.findByDateOrderByHourAsc(date);

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
            .max((h1, h2) -> Integer.compare(h1.getStepCount(), h2.getStepCount()))
            .orElse(null);

        int activeHoursCount = (int) hourlyData.stream()
            .filter(h -> h.getStepCount() > 0)
            .count();

        Optional<StepsDailyProjectionJpaEntity> existingOpt = dailyRepository.findByDate(date);

        StepsDailyProjectionJpaEntity daily;

        daily = existingOpt.orElseGet(() -> StepsDailyProjectionJpaEntity.builder()
                .date(date)
                .build());

        daily.setTotalSteps(totalSteps);
        daily.setFirstStepTime(firstStepTime);
        daily.setLastStepTime(lastStepTime);
        daily.setActiveHoursCount(activeHoursCount);

        daily.setMostActiveHour(mostActiveHourData.getHour());
        daily.setMostActiveHourSteps(mostActiveHourData.getStepCount());

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

    private Integer getInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
