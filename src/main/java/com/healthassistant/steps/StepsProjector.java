package com.healthassistant.steps;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
class StepsProjector {

    private final StepsDailyProjectionJpaRepository dailyRepository;
    private final StepsHourlyProjectionJpaRepository hourlyRepository;
    private final StepsBucketFactory stepsBucketFactory;

    @Transactional
    public void projectSteps(StoredEventData eventData) {
        try {
            stepsBucketFactory.createFromEvent(eventData)
                    .ifPresent(this::saveProjection);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Race condition during steps projection for event {}, skipping", eventData.eventId().value());
        }
    }

    private synchronized void saveProjection(StepsBucket bucket) {
        StepsHourlyProjectionJpaEntity hourly = hourlyRepository
                .findByDateAndHour(bucket.date(), bucket.hour())
                .map(existing -> {
                    existing.addBucket(bucket);
                    return existing;
                })
                .orElseGet(() -> StepsHourlyProjectionJpaEntity.from(bucket));

        hourlyRepository.save(hourly);
        updateDailyProjection(bucket.date());
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
                .max(Comparator.comparingInt(StepsHourlyProjectionJpaEntity::getStepCount))
                .orElse(null);

        int activeHoursCount = (int) hourlyData.stream()
                .filter(h -> h.getStepCount() > 0)
                .count();

        StepsDailyProjectionJpaEntity daily = dailyRepository.findByDate(date)
                .orElseGet(() -> StepsDailyProjectionJpaEntity.builder()
                        .date(date)
                        .build());

        daily.setTotalSteps(totalSteps);
        daily.setFirstStepTime(firstStepTime);
        daily.setLastStepTime(lastStepTime);
        daily.setActiveHoursCount(activeHoursCount);

        if (mostActiveHourData != null) {
            daily.setMostActiveHour(mostActiveHourData.getHour());
            daily.setMostActiveHourSteps(mostActiveHourData.getStepCount());
        }

        dailyRepository.save(daily);
    }
}
