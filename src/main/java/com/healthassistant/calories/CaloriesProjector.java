package com.healthassistant.calories;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.ActiveCaloriesPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
class CaloriesProjector {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final CaloriesDailyProjectionJpaRepository dailyRepository;
    private final CaloriesHourlyProjectionJpaRepository hourlyRepository;

    @Transactional
    public void projectCalories(StoredEventData eventData) {
        try {
            projectActiveCalories(eventData);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Race condition during calories projection for event {}, skipping", eventData.eventId().value());
        }
    }

    private void projectActiveCalories(StoredEventData eventData) {
        if (!(eventData.payload() instanceof ActiveCaloriesPayload payload)) {
            log.warn("Expected ActiveCaloriesPayload but got {}, skipping projection",
                    eventData.payload().getClass().getSimpleName());
            return;
        }

        String deviceId = eventData.deviceId().value();
        Instant bucketStart = payload.bucketStart();
        Instant bucketEnd = payload.bucketEnd();
        Double energyKcal = payload.energyKcal();

        if (bucketStart == null || bucketEnd == null) {
            log.warn("ActiveCaloriesBurned event missing bucketStart or bucketEnd, skipping projection");
            return;
        }

        if (energyKcal == null || energyKcal <= 0) {
            log.debug("ActiveCaloriesBurned event has zero or negative calories, skipping projection");
            return;
        }

        ZonedDateTime startZoned = bucketStart.atZone(POLAND_ZONE);
        LocalDate date = startZoned.toLocalDate();
        int hour = startZoned.getHour();

        updateHourlyProjection(deviceId, date, hour, energyKcal, bucketStart, bucketEnd);
    }

    private synchronized void updateHourlyProjection(
            String deviceId,
            LocalDate date,
            int hour,
            double caloriesKcal,
            Instant bucketStart,
            Instant bucketEnd
    ) {
        Optional<CaloriesHourlyProjectionJpaEntity> existingOpt =
            hourlyRepository.findByDeviceIdAndDateAndHour(deviceId, date, hour);

        CaloriesHourlyProjectionJpaEntity hourly;

        if (existingOpt.isPresent()) {
            hourly = existingOpt.get();
            hourly.setCaloriesKcal(hourly.getCaloriesKcal() + caloriesKcal);
            hourly.setBucketCount(hourly.getBucketCount() + 1);

            if (hourly.getFirstBucketTime() == null || bucketStart.isBefore(hourly.getFirstBucketTime())) {
                hourly.setFirstBucketTime(bucketStart);
            }
            if (hourly.getLastBucketTime() == null || bucketEnd.isAfter(hourly.getLastBucketTime())) {
                hourly.setLastBucketTime(bucketEnd);
            }
        } else {
            hourly = CaloriesHourlyProjectionJpaEntity.builder()
                .deviceId(deviceId)
                .date(date)
                .hour(hour)
                .caloriesKcal(caloriesKcal)
                .bucketCount(1)
                .firstBucketTime(bucketStart)
                .lastBucketTime(bucketEnd)
                .build();
        }

        hourlyRepository.save(hourly);

        updateDailyProjection(deviceId, date);
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
            .max((h1, h2) -> Double.compare(h1.getCaloriesKcal(), h2.getCaloriesKcal()))
            .orElse(null);

        int activeHoursCount = (int) hourlyData.stream()
            .filter(h -> h.getCaloriesKcal() > 0)
            .count();

        Optional<CaloriesDailyProjectionJpaEntity> existingOpt = dailyRepository.findByDeviceIdAndDate(deviceId, date);

        CaloriesDailyProjectionJpaEntity daily = existingOpt.orElseGet(() -> CaloriesDailyProjectionJpaEntity.builder()
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
