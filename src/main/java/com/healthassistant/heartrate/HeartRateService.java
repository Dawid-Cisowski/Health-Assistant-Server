package com.healthassistant.heartrate;

import com.healthassistant.heartrate.api.HeartRateFacade;
import com.healthassistant.heartrate.api.dto.HeartRateDataPointResponse;
import com.healthassistant.heartrate.api.dto.HeartRateRangeResponse;
import com.healthassistant.heartrate.api.dto.RestingHeartRateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
class HeartRateService implements HeartRateFacade {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final HeartRateRepository heartRateRepository;
    private final RestingHeartRateRepository restingHeartRateRepository;

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() < 8) return "***";
        return deviceId.substring(0, 4) + "..." + deviceId.substring(deviceId.length() - 4);
    }

    @Override
    @Transactional(readOnly = true)
    public HeartRateRangeResponse getRange(String deviceId, LocalDate from, LocalDate to) {
        Instant startTime = from.atStartOfDay(POLAND_ZONE).toInstant();
        Instant endTime = to.plusDays(1).atStartOfDay(POLAND_ZONE).toInstant();

        List<HeartRateProjectionJpaEntity> entities = heartRateRepository.findByDeviceIdAndTimeRange(
                deviceId, startTime, endTime
        );

        List<HeartRateDataPointResponse> dataPoints = entities.stream()
                .map(this::toDataPointResponse)
                .toList();

        HeartRateAggregates aggregates = calculateAggregates(entities);

        return new HeartRateRangeResponse(
                from,
                to,
                dataPoints.size(),
                aggregates.avgBpm(),
                aggregates.minBpm(),
                aggregates.maxBpm(),
                dataPoints
        );
    }

    private HeartRateAggregates calculateAggregates(List<HeartRateProjectionJpaEntity> entities) {
        if (entities.isEmpty()) {
            return new HeartRateAggregates(null, null, null);
        }

        int totalSamples = entities.stream().mapToInt(HeartRateProjectionJpaEntity::getSamples).sum();
        long weightedSum = entities.stream()
                .mapToLong(e -> (long) e.getAvgBpm() * e.getSamples())
                .sum();
        Integer avgBpm = totalSamples > 0 ? (int) (weightedSum / totalSamples) : null;

        Integer minBpm = entities.stream()
                .filter(e -> e.getMinBpm() != null)
                .mapToInt(HeartRateProjectionJpaEntity::getMinBpm)
                .min()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);

        Integer maxBpm = entities.stream()
                .filter(e -> e.getMaxBpm() != null)
                .mapToInt(HeartRateProjectionJpaEntity::getMaxBpm)
                .max()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);

        return new HeartRateAggregates(avgBpm, minBpm, maxBpm);
    }

    private record HeartRateAggregates(Integer avgBpm, Integer minBpm, Integer maxBpm) {
    }

    @Override
    @Transactional(readOnly = true)
    public List<RestingHeartRateResponse> getRestingRange(String deviceId, LocalDate from, LocalDate to) {
        List<RestingHeartRateProjectionJpaEntity> entities = restingHeartRateRepository.findByDeviceIdAndDateRange(
                deviceId, from, to
        );

        return entities.stream()
                .map(this::toRestingResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Integer> getRestingBpmForDate(String deviceId, LocalDate date) {
        return restingHeartRateRepository.findByDeviceIdAndDate(deviceId, date)
                .map(RestingHeartRateProjectionJpaEntity::getRestingBpm);
    }

    @Override
    @Transactional
    public void deleteProjectionsForDate(String deviceId, LocalDate date) {
        Instant startTime = date.atStartOfDay(POLAND_ZONE).toInstant();
        Instant endTime = date.plusDays(1).atStartOfDay(POLAND_ZONE).toInstant();
        heartRateRepository.deleteByDeviceIdAndTimeRange(deviceId, startTime, endTime);
        restingHeartRateRepository.deleteByDeviceIdAndDate(deviceId, date);
        log.debug("Deleted HR projections for device {} on date {}", maskDeviceId(deviceId), date);
    }

    private HeartRateDataPointResponse toDataPointResponse(HeartRateProjectionJpaEntity entity) {
        return new HeartRateDataPointResponse(
                entity.getMeasuredAt(),
                entity.getAvgBpm(),
                entity.getMinBpm(),
                entity.getMaxBpm(),
                entity.getSamples()
        );
    }

    private RestingHeartRateResponse toRestingResponse(RestingHeartRateProjectionJpaEntity entity) {
        return new RestingHeartRateResponse(
                entity.getDate(),
                entity.getRestingBpm(),
                entity.getMeasuredAt()
        );
    }
}
