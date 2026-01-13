package com.healthassistant.weight;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.weight.api.WeightFacade;
import com.healthassistant.weight.api.dto.WeightLatestResponse;
import com.healthassistant.weight.api.dto.WeightMeasurementResponse;
import com.healthassistant.weight.api.dto.WeightRangeSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
class WeightService implements WeightFacade {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final WeightMeasurementProjectionJpaRepository repository;
    private final WeightProjector weightProjector;

    @Override
    public Optional<WeightLatestResponse> getLatestMeasurement(String deviceId) {
        return repository.findFirstByDeviceIdOrderByMeasuredAtDesc(deviceId)
                .map(entity -> {
                    WeightMeasurementResponse measurement = toResponse(entity);
                    WeightLatestResponse.TrendData trends = calculateTrends(deviceId, entity);
                    return new WeightLatestResponse(measurement, trends);
                });
    }

    private WeightLatestResponse.TrendData calculateTrends(String deviceId, WeightMeasurementProjectionJpaEntity current) {
        Instant now = current.getMeasuredAt();
        Instant thirtyDaysAgo = now.atZone(POLAND_ZONE).minusDays(30).toInstant();
        Instant sevenDaysAgo = now.atZone(POLAND_ZONE).minusDays(7).toInstant();

        Optional<WeightMeasurementProjectionJpaEntity> previousOpt =
                repository.findFirstByDeviceIdAndMeasuredAtLessThanOrderByMeasuredAtDesc(deviceId, now);

        BigDecimal weightChangeVsPrevious = previousOpt
                .map(prev -> current.getWeightKg().subtract(prev.getWeightKg()))
                .orElse(null);

        BigDecimal bodyFatChangeVsPrevious = previousOpt
                .filter(prev -> prev.getBodyFatPercent() != null && current.getBodyFatPercent() != null)
                .map(prev -> current.getBodyFatPercent().subtract(prev.getBodyFatPercent()))
                .orElse(null);

        BigDecimal muscleChangeVsPrevious = previousOpt
                .filter(prev -> prev.getMusclePercent() != null && current.getMusclePercent() != null)
                .map(prev -> current.getMusclePercent().subtract(prev.getMusclePercent()))
                .orElse(null);

        List<WeightMeasurementProjectionJpaEntity> last30DaysMeasurements =
                repository.findByDeviceIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(deviceId, thirtyDaysAgo, now);

        BigDecimal weightChange30Days = last30DaysMeasurements.stream()
                .min(Comparator.comparing(WeightMeasurementProjectionJpaEntity::getMeasuredAt))
                .map(oldest -> current.getWeightKg().subtract(oldest.getWeightKg()))
                .orElse(null);

        List<WeightMeasurementProjectionJpaEntity> last7DaysMeasurements =
                repository.findByDeviceIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(deviceId, sevenDaysAgo, now);

        BigDecimal weightChange7Days = last7DaysMeasurements.stream()
                .min(Comparator.comparing(WeightMeasurementProjectionJpaEntity::getMeasuredAt))
                .map(oldest -> current.getWeightKg().subtract(oldest.getWeightKg()))
                .orElse(null);

        int measurementsInLast30Days = (int) repository.countByDeviceIdAndMeasuredAtBetween(deviceId, thirtyDaysAgo, now);

        return new WeightLatestResponse.TrendData(
                weightChangeVsPrevious,
                weightChange7Days,
                weightChange30Days,
                bodyFatChangeVsPrevious,
                muscleChangeVsPrevious,
                measurementsInLast30Days
        );
    }

    @Override
    public WeightRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate) {
        List<WeightMeasurementProjectionJpaEntity> measurements =
                repository.findByDeviceIdAndDateBetweenOrderByMeasuredAtAsc(deviceId, startDate, endDate);

        if (measurements.isEmpty()) {
            return new WeightRangeSummaryResponse(
                    startDate, endDate, 0, 0, null, null, null, null, null, null, null, null, List.of()
            );
        }

        int daysWithData = (int) measurements.stream()
                .map(WeightMeasurementProjectionJpaEntity::getDate)
                .distinct()
                .count();

        BigDecimal startWeight = measurements.getFirst().getWeightKg();
        BigDecimal endWeight = measurements.getLast().getWeightKg();
        BigDecimal weightChangeKg = endWeight.subtract(startWeight);

        BigDecimal minWeight = measurements.stream()
                .map(WeightMeasurementProjectionJpaEntity::getWeightKg)
                .min(BigDecimal::compareTo)
                .orElse(null);

        BigDecimal maxWeight = measurements.stream()
                .map(WeightMeasurementProjectionJpaEntity::getWeightKg)
                .max(BigDecimal::compareTo)
                .orElse(null);

        BigDecimal averageWeight = measurements.stream()
                .map(WeightMeasurementProjectionJpaEntity::getWeightKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(measurements.size()), 2, RoundingMode.HALF_UP);

        BigDecimal averageBodyFat = calculateAverageOrNull(measurements.stream()
                .map(WeightMeasurementProjectionJpaEntity::getBodyFatPercent)
                .filter(java.util.Objects::nonNull)
                .toList());

        BigDecimal averageMuscle = calculateAverageOrNull(measurements.stream()
                .map(WeightMeasurementProjectionJpaEntity::getMusclePercent)
                .filter(java.util.Objects::nonNull)
                .toList());

        List<WeightMeasurementResponse> responses = measurements.stream()
                .map(this::toResponse)
                .toList();

        return new WeightRangeSummaryResponse(
                startDate, endDate, measurements.size(), daysWithData, startWeight, endWeight,
                weightChangeKg, minWeight, maxWeight, averageWeight, averageBodyFat, averageMuscle, responses
        );
    }

    private BigDecimal calculateAverageOrNull(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    @Override
    public Optional<WeightMeasurementResponse> getMeasurementById(String deviceId, String measurementId) {
        return repository.findByDeviceIdAndMeasurementId(deviceId, measurementId)
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public void deleteAllProjections() {
        log.warn("Deleting all weight projections");
        repository.deleteAll();
    }

    @Override
    @Transactional
    public void deleteProjectionsByDeviceId(String deviceId) {
        log.debug("Deleting weight projections for device: {}", WeightSecurityUtils.maskDeviceId(deviceId));
        repository.deleteByDeviceId(deviceId);
    }

    @Override
    @Transactional
    public void deleteProjectionsForDate(String deviceId, LocalDate date) {
        log.debug("Deleting weight projections for device {} date {}", WeightSecurityUtils.maskDeviceId(deviceId), date);
        repository.deleteByDeviceIdAndDate(deviceId, date);
    }

    @Override
    @Transactional
    public void projectEvents(List<StoredEventData> events) {
        log.debug("Projecting {} weight events directly", events.size());

        List<String> failedEventIds = events.stream()
                .filter(event -> !tryProjectEvent(event))
                .map(event -> event.eventId().value())
                .toList();

        if (!failedEventIds.isEmpty()) {
            log.error("Failed to project {} weight events: {}", failedEventIds.size(), failedEventIds);
        }
    }

    private boolean tryProjectEvent(StoredEventData event) {
        try {
            weightProjector.projectWeight(event);
            return true;
        } catch (Exception e) {
            log.error("Failed to project weight event: {}", event.eventId().value(), e);
            return false;
        }
    }

    private WeightMeasurementResponse toResponse(WeightMeasurementProjectionJpaEntity entity) {
        return new WeightMeasurementResponse(
                entity.getMeasurementId(),
                entity.getDate(),
                entity.getMeasuredAt(),
                entity.getScore(),
                entity.getWeightKg(),
                entity.getBmi(),
                entity.getBodyFatPercent(),
                entity.getMusclePercent(),
                entity.getHydrationPercent(),
                entity.getBoneMassKg(),
                entity.getBmrKcal(),
                entity.getVisceralFatLevel(),
                entity.getSubcutaneousFatPercent(),
                entity.getProteinPercent(),
                entity.getMetabolicAge(),
                entity.getIdealWeightKg(),
                entity.getWeightControlKg(),
                entity.getFatMassKg(),
                entity.getLeanBodyMassKg(),
                entity.getMuscleMassKg(),
                entity.getProteinMassKg(),
                entity.getBodyType(),
                entity.getSource()
        );
    }

}
