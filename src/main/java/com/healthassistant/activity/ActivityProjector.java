package com.healthassistant.activity;

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
class ActivityProjector {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final ActivityDailyProjectionJpaRepository dailyRepository;
    private final ActivityHourlyProjectionJpaRepository hourlyRepository;

    @Transactional
    public void projectActivity(StoredEventData eventData) {
        try {
            projectActiveMinutes(eventData);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Race condition during activity projection for event {}, skipping", eventData.eventId().value());
        }
    }

    private void projectActiveMinutes(StoredEventData eventData) {
        Map<String, Object> payload = eventData.payload();
        String deviceId = eventData.deviceId().value();

        Instant bucketStart = parseInstant(payload.get("bucketStart"));
        Instant bucketEnd = parseInstant(payload.get("bucketEnd"));
        Integer activeMinutes = getInteger(payload);

        if (bucketStart == null || bucketEnd == null) {
            log.warn("ActiveMinutesRecorded event missing bucketStart or bucketEnd, skipping projection");
            return;
        }

        if (activeMinutes == null || activeMinutes <= 0) {
            log.debug("ActiveMinutesRecorded event has zero or negative minutes, skipping projection");
            return;
        }

        ZonedDateTime startZoned = bucketStart.atZone(POLAND_ZONE);
        LocalDate date = startZoned.toLocalDate();
        int hour = startZoned.getHour();

        updateHourlyProjection(deviceId, date, hour, activeMinutes, bucketStart, bucketEnd);
    }

    private synchronized void updateHourlyProjection(
            String deviceId,
            LocalDate date,
            int hour,
            int activeMinutes,
            Instant bucketStart,
            Instant bucketEnd
    ) {
        Optional<ActivityHourlyProjectionJpaEntity> existingOpt =
                hourlyRepository.findByDeviceIdAndDateAndHour(deviceId, date, hour);

        ActivityHourlyProjectionJpaEntity hourly;

        if (existingOpt.isPresent()) {
            hourly = existingOpt.get();
            hourly.setActiveMinutes(hourly.getActiveMinutes() + activeMinutes);
            hourly.setBucketCount(hourly.getBucketCount() + 1);

            if (hourly.getFirstBucketTime() == null || bucketStart.isBefore(hourly.getFirstBucketTime())) {
                hourly.setFirstBucketTime(bucketStart);
            }
            if (hourly.getLastBucketTime() == null || bucketEnd.isAfter(hourly.getLastBucketTime())) {
                hourly.setLastBucketTime(bucketEnd);
            }
        } else {
            hourly = ActivityHourlyProjectionJpaEntity.builder()
                    .deviceId(deviceId)
                    .date(date)
                    .hour(hour)
                    .activeMinutes(activeMinutes)
                    .bucketCount(1)
                    .firstBucketTime(bucketStart)
                    .lastBucketTime(bucketEnd)
                    .build();
        }

        hourlyRepository.save(hourly);

        updateDailyProjection(deviceId, date);
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
                .max((h1, h2) -> Integer.compare(h1.getActiveMinutes(), h2.getActiveMinutes()))
                .orElse(null);

        int activeHoursCount = (int) hourlyData.stream()
                .filter(h -> h.getActiveMinutes() > 0)
                .count();

        Optional<ActivityDailyProjectionJpaEntity> existingOpt = dailyRepository.findByDeviceIdAndDate(deviceId, date);

        ActivityDailyProjectionJpaEntity daily = existingOpt.orElseGet(() -> ActivityDailyProjectionJpaEntity.builder()
                .deviceId(deviceId)
                .date(date)
                .build());

        daily.setTotalActiveMinutes(totalMinutes);
        daily.setFirstActivityTime(firstActivityTime);
        daily.setLastActivityTime(lastActivityTime);
        daily.setActiveHoursCount(activeHoursCount);

        daily.setMostActiveHour(mostActiveHourData.getHour());
        daily.setMostActiveHourMinutes(mostActiveHourData.getActiveMinutes());

        dailyRepository.save(daily);
    }

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

    private Integer getInteger(Map<String, Object> map) {
        Object value = map.get("activeMinutes");
        if (value == null) return 0;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return 0;
        }
    }
}
