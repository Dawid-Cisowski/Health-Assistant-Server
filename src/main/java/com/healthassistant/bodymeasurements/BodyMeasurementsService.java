package com.healthassistant.bodymeasurements;

import com.healthassistant.bodymeasurements.api.BodyMeasurementsFacade;
import com.healthassistant.bodymeasurements.api.dto.BodyMeasurementLatestResponse;
import com.healthassistant.bodymeasurements.api.dto.BodyMeasurementRangeSummaryResponse;
import com.healthassistant.bodymeasurements.api.dto.BodyMeasurementResponse;
import com.healthassistant.bodymeasurements.api.dto.BodyMeasurementSummaryResponse;
import com.healthassistant.bodymeasurements.api.dto.BodyPart;
import com.healthassistant.bodymeasurements.api.dto.BodyPartHistoryResponse;
import com.healthassistant.healthevents.api.dto.StoredEventData;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
class BodyMeasurementsService implements BodyMeasurementsFacade {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final BodyMeasurementProjectionJpaRepository repository;
    private final BodyMeasurementsProjector bodyMeasurementsProjector;

    @Override
    public Optional<BodyMeasurementLatestResponse> getLatestMeasurement(String deviceId) {
        return repository.findFirstByDeviceIdOrderByMeasuredAtDesc(deviceId)
                .map(entity -> {
                    BodyMeasurementResponse measurement = toResponse(entity);
                    BodyMeasurementLatestResponse.TrendData trends = calculateTrends(deviceId, entity);
                    return new BodyMeasurementLatestResponse(measurement, trends);
                });
    }

    private BodyMeasurementLatestResponse.TrendData calculateTrends(String deviceId, BodyMeasurementProjectionJpaEntity current) {
        Instant now = current.getMeasuredAt();
        Instant thirtyDaysAgo = now.atZone(POLAND_ZONE).minusDays(30).toInstant();

        Optional<BodyMeasurementProjectionJpaEntity> previousOpt =
                repository.findFirstByDeviceIdAndMeasuredAtLessThanOrderByMeasuredAtDesc(deviceId, now);

        BigDecimal bicepsLeftChange = calculateChange(previousOpt, current,
                BodyMeasurementProjectionJpaEntity::getBicepsLeftCm);
        BigDecimal bicepsRightChange = calculateChange(previousOpt, current,
                BodyMeasurementProjectionJpaEntity::getBicepsRightCm);
        BigDecimal chestChange = calculateChange(previousOpt, current,
                BodyMeasurementProjectionJpaEntity::getChestCm);
        BigDecimal waistChange = calculateChange(previousOpt, current,
                BodyMeasurementProjectionJpaEntity::getWaistCm);
        BigDecimal thighLeftChange = calculateChange(previousOpt, current,
                BodyMeasurementProjectionJpaEntity::getThighLeftCm);
        BigDecimal thighRightChange = calculateChange(previousOpt, current,
                BodyMeasurementProjectionJpaEntity::getThighRightCm);

        int measurementsInLast30Days = (int) repository.countByDeviceIdAndMeasuredAtBetween(deviceId, thirtyDaysAgo, now);

        return new BodyMeasurementLatestResponse.TrendData(
                bicepsLeftChange,
                bicepsRightChange,
                chestChange,
                waistChange,
                thighLeftChange,
                thighRightChange,
                measurementsInLast30Days
        );
    }

    private BigDecimal calculateChange(
            Optional<BodyMeasurementProjectionJpaEntity> previousOpt,
            BodyMeasurementProjectionJpaEntity current,
            Function<BodyMeasurementProjectionJpaEntity, BigDecimal> getter) {
        return previousOpt
                .filter(prev -> getter.apply(prev) != null && getter.apply(current) != null)
                .map(prev -> getter.apply(current).subtract(getter.apply(prev)))
                .orElse(null);
    }

    @Override
    public BodyMeasurementRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate) {
        List<BodyMeasurementProjectionJpaEntity> measurements =
                repository.findByDeviceIdAndDateBetweenOrderByMeasuredAtAsc(deviceId, startDate, endDate);

        if (measurements.isEmpty()) {
            return new BodyMeasurementRangeSummaryResponse(
                    startDate, endDate, 0, 0, List.of()
            );
        }

        int daysWithData = (int) measurements.stream()
                .map(BodyMeasurementProjectionJpaEntity::getDate)
                .distinct()
                .count();

        List<BodyMeasurementResponse> responses = measurements.stream()
                .map(this::toResponse)
                .toList();

        return new BodyMeasurementRangeSummaryResponse(
                startDate, endDate, measurements.size(), daysWithData, responses
        );
    }

    @Override
    public Optional<BodyMeasurementResponse> getMeasurementById(String deviceId, String measurementId) {
        return repository.findByDeviceIdAndMeasurementId(deviceId, measurementId)
                .map(this::toResponse);
    }

    private static final List<MeasurementMapping> MEASUREMENT_MAPPINGS = List.of(
            new MeasurementMapping("bicepsLeft", BodyMeasurementProjectionJpaEntity::getBicepsLeftCm),
            new MeasurementMapping("bicepsRight", BodyMeasurementProjectionJpaEntity::getBicepsRightCm),
            new MeasurementMapping("forearmLeft", BodyMeasurementProjectionJpaEntity::getForearmLeftCm),
            new MeasurementMapping("forearmRight", BodyMeasurementProjectionJpaEntity::getForearmRightCm),
            new MeasurementMapping("chest", BodyMeasurementProjectionJpaEntity::getChestCm),
            new MeasurementMapping("waist", BodyMeasurementProjectionJpaEntity::getWaistCm),
            new MeasurementMapping("abdomen", BodyMeasurementProjectionJpaEntity::getAbdomenCm),
            new MeasurementMapping("hips", BodyMeasurementProjectionJpaEntity::getHipsCm),
            new MeasurementMapping("neck", BodyMeasurementProjectionJpaEntity::getNeckCm),
            new MeasurementMapping("shoulders", BodyMeasurementProjectionJpaEntity::getShouldersCm),
            new MeasurementMapping("thighLeft", BodyMeasurementProjectionJpaEntity::getThighLeftCm),
            new MeasurementMapping("thighRight", BodyMeasurementProjectionJpaEntity::getThighRightCm),
            new MeasurementMapping("calfLeft", BodyMeasurementProjectionJpaEntity::getCalfLeftCm),
            new MeasurementMapping("calfRight", BodyMeasurementProjectionJpaEntity::getCalfRightCm)
    );

    @Override
    public BodyMeasurementSummaryResponse getSummary(String deviceId) {
        Optional<BodyMeasurementProjectionJpaEntity> latestOpt =
                repository.findFirstByDeviceIdOrderByMeasuredAtDesc(deviceId);

        if (latestOpt.isEmpty()) {
            return new BodyMeasurementSummaryResponse(null, Map.of());
        }

        BodyMeasurementProjectionJpaEntity latest = latestOpt.get();
        Optional<BodyMeasurementProjectionJpaEntity> previousOpt =
                repository.findFirstByDeviceIdAndMeasuredAtLessThanOrderByMeasuredAtDesc(deviceId, latest.getMeasuredAt());

        Map<String, BodyMeasurementSummaryResponse.MeasurementWithChange> measurements = MEASUREMENT_MAPPINGS.stream()
                .filter(mapping -> mapping.getter().apply(latest) != null)
                .collect(java.util.stream.Collectors.toMap(
                        MeasurementMapping::key,
                        mapping -> {
                            BigDecimal current = mapping.getter().apply(latest);
                            BigDecimal previous = previousOpt.map(mapping.getter()).orElse(null);
                            BigDecimal change = (previous != null) ? current.subtract(previous) : null;
                            return BodyMeasurementSummaryResponse.MeasurementWithChange.of(current, change);
                        },
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        return new BodyMeasurementSummaryResponse(latest.getMeasuredAt(), measurements);
    }

    private record MeasurementMapping(
            String key,
            Function<BodyMeasurementProjectionJpaEntity, BigDecimal> getter
    ) {}

    @Override
    public BodyPartHistoryResponse getBodyPartHistory(String deviceId, BodyPart bodyPart, LocalDate from, LocalDate to) {
        List<BodyMeasurementProjectionJpaEntity> measurements =
                repository.findByDeviceIdAndDateBetweenOrderByMeasuredAtAsc(deviceId, from, to);

        Function<BodyMeasurementProjectionJpaEntity, BigDecimal> valueGetter = getValueGetter(bodyPart);

        List<BodyPartHistoryResponse.DataPoint> dataPoints = measurements.stream()
                .filter(m -> valueGetter.apply(m) != null)
                .map(m -> new BodyPartHistoryResponse.DataPoint(m.getDate(), valueGetter.apply(m)))
                .toList();

        if (dataPoints.isEmpty()) {
            return BodyPartHistoryResponse.empty(bodyPart);
        }

        BigDecimal min = dataPoints.stream()
                .map(BodyPartHistoryResponse.DataPoint::value)
                .min(BigDecimal::compareTo)
                .orElse(null);

        BigDecimal max = dataPoints.stream()
                .map(BodyPartHistoryResponse.DataPoint::value)
                .max(BigDecimal::compareTo)
                .orElse(null);

        BigDecimal firstValue = dataPoints.getFirst().value();
        BigDecimal lastValue = dataPoints.getLast().value();
        BigDecimal change = lastValue.subtract(firstValue);
        BigDecimal changePercent = firstValue.compareTo(BigDecimal.ZERO) > 0
                ? change.multiply(BigDecimal.valueOf(100)).divide(firstValue, 1, RoundingMode.HALF_UP)
                : null;

        BodyPartHistoryResponse.Statistics statistics = new BodyPartHistoryResponse.Statistics(
                min, max, change, changePercent
        );

        return new BodyPartHistoryResponse(bodyPart, "cm", dataPoints, statistics);
    }

    private Function<BodyMeasurementProjectionJpaEntity, BigDecimal> getValueGetter(BodyPart bodyPart) {
        return switch (bodyPart) {
            case BICEPS_LEFT -> BodyMeasurementProjectionJpaEntity::getBicepsLeftCm;
            case BICEPS_RIGHT -> BodyMeasurementProjectionJpaEntity::getBicepsRightCm;
            case FOREARM_LEFT -> BodyMeasurementProjectionJpaEntity::getForearmLeftCm;
            case FOREARM_RIGHT -> BodyMeasurementProjectionJpaEntity::getForearmRightCm;
            case CHEST -> BodyMeasurementProjectionJpaEntity::getChestCm;
            case WAIST -> BodyMeasurementProjectionJpaEntity::getWaistCm;
            case ABDOMEN -> BodyMeasurementProjectionJpaEntity::getAbdomenCm;
            case HIPS -> BodyMeasurementProjectionJpaEntity::getHipsCm;
            case NECK -> BodyMeasurementProjectionJpaEntity::getNeckCm;
            case SHOULDERS -> BodyMeasurementProjectionJpaEntity::getShouldersCm;
            case THIGH_LEFT -> BodyMeasurementProjectionJpaEntity::getThighLeftCm;
            case THIGH_RIGHT -> BodyMeasurementProjectionJpaEntity::getThighRightCm;
            case CALF_LEFT -> BodyMeasurementProjectionJpaEntity::getCalfLeftCm;
            case CALF_RIGHT -> BodyMeasurementProjectionJpaEntity::getCalfRightCm;
        };
    }

    @Override
    @Transactional
    public void deleteProjectionsForDate(String deviceId, LocalDate date) {
        log.debug("Deleting body measurement projections for device {} date {}",
                BodyMeasurementsSecurityUtils.maskDeviceId(deviceId), date);
        repository.deleteByDeviceIdAndDate(deviceId, date);
    }

    @Override
    @Transactional
    public void projectEvents(List<StoredEventData> events) {
        log.debug("Projecting {} body measurement events directly", events.size());

        List<String> failedEventIds = events.stream()
                .filter(event -> !tryProjectEvent(event))
                .map(event -> event.eventId().value())
                .toList();

        if (!failedEventIds.isEmpty()) {
            log.error("Failed to project {} body measurement events: {}", failedEventIds.size(), failedEventIds);
        }
    }

    private boolean tryProjectEvent(StoredEventData event) {
        try {
            bodyMeasurementsProjector.projectBodyMeasurement(event);
            return true;
        } catch (Exception e) {
            log.error("Failed to project body measurement event: {}", event.eventId().value(), e);
            return false;
        }
    }

    private BodyMeasurementResponse toResponse(BodyMeasurementProjectionJpaEntity entity) {
        return new BodyMeasurementResponse(
                entity.getMeasurementId(),
                entity.getDate(),
                entity.getMeasuredAt(),
                entity.getBicepsLeftCm(),
                entity.getBicepsRightCm(),
                entity.getForearmLeftCm(),
                entity.getForearmRightCm(),
                entity.getChestCm(),
                entity.getWaistCm(),
                entity.getAbdomenCm(),
                entity.getHipsCm(),
                entity.getNeckCm(),
                entity.getShouldersCm(),
                entity.getThighLeftCm(),
                entity.getThighRightCm(),
                entity.getCalfLeftCm(),
                entity.getCalfRightCm(),
                entity.getNotes()
        );
    }
}
