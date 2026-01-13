package com.healthassistant.heartrate;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.HeartRatePayload;
import com.healthassistant.healthevents.api.dto.payload.RestingHeartRatePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
class HeartRateProjector {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final HeartRateRepository heartRateRepository;
    private final RestingHeartRateRepository restingHeartRateRepository;

    @Transactional
    public void projectHeartRateSummary(StoredEventData eventData) {
        Objects.requireNonNull(eventData, "eventData cannot be null");

        if (eventData.deviceId() == null || eventData.eventId() == null) {
            log.error("Invalid event data: missing deviceId or eventId, skipping projection");
            return;
        }

        if (!(eventData.payload() instanceof HeartRatePayload payload)) {
            log.error("Invalid payload type for heart rate event: {}", eventData.eventId().value());
            return;
        }

        try {
            doProjectHeartRateSummary(eventData.deviceId().value(), eventData.eventId().value(), payload);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Version conflict for HR projection {}, retrying once", eventData.eventId().value());
            doProjectHeartRateSummary(eventData.deviceId().value(), eventData.eventId().value(), payload);
        }
    }

    private void doProjectHeartRateSummary(String deviceId, String eventId, HeartRatePayload payload) {
        var existingOpt = heartRateRepository.findByEventId(eventId);

        HeartRateProjectionJpaEntity entity;

        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            entity.updateMeasurement(
                    payload.avg().intValue(),
                    payload.min(),
                    payload.max(),
                    payload.samples()
            );
            log.debug("Updating existing HR projection for event {}", eventId);
        } else {
            entity = HeartRateProjectionJpaEntity.create(
                    deviceId,
                    eventId,
                    payload.bucketStart(),
                    payload.avg().intValue(),
                    payload.min(),
                    payload.max(),
                    payload.samples()
            );
            log.debug("Creating new HR projection at {}", payload.bucketStart());
        }

        heartRateRepository.save(entity);
    }

    @Transactional
    public void projectRestingHeartRate(StoredEventData eventData) {
        Objects.requireNonNull(eventData, "eventData cannot be null");

        if (eventData.deviceId() == null || eventData.eventId() == null) {
            log.error("Invalid event data: missing deviceId or eventId, skipping projection");
            return;
        }

        if (!(eventData.payload() instanceof RestingHeartRatePayload payload)) {
            log.error("Invalid payload type for resting HR event: {}", eventData.eventId().value());
            return;
        }

        try {
            doProjectRestingHeartRate(eventData.deviceId().value(), eventData.eventId().value(), payload);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Version conflict for resting HR projection {}, retrying once", eventData.eventId().value());
            doProjectRestingHeartRate(eventData.deviceId().value(), eventData.eventId().value(), payload);
        }
    }

    private void doProjectRestingHeartRate(String deviceId, String eventId, RestingHeartRatePayload payload) {
        LocalDate date = payload.measuredAt().atZone(POLAND_ZONE).toLocalDate();

        var existingOpt = restingHeartRateRepository.findByDeviceIdAndDate(deviceId, date)
                .or(() -> restingHeartRateRepository.findByEventId(eventId));

        RestingHeartRateProjectionJpaEntity entity;

        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            entity.updateRestingBpm(payload.restingBpm(), payload.measuredAt());
            entity.updateEventId(eventId);
            log.debug("Updating existing resting HR projection for date {} with event {}", date, eventId);
        } else {
            entity = RestingHeartRateProjectionJpaEntity.create(
                    deviceId,
                    eventId,
                    date,
                    payload.restingBpm(),
                    payload.measuredAt()
            );
            log.info("Creating new resting HR projection: {} bpm on {}", payload.restingBpm(), date);
        }

        restingHeartRateRepository.save(entity);
    }

    @Transactional
    public void deleteByEventId(String eventId) {
        int deletedHR = heartRateRepository.deleteByEventId(eventId);
        if (deletedHR > 0) {
            log.info("Deleted {} HR projection(s) for eventId: {}", deletedHR, eventId);
        }

        int deletedRestingHR = restingHeartRateRepository.deleteByEventId(eventId);
        if (deletedRestingHR > 0) {
            log.info("Deleted {} resting HR projection(s) for eventId: {}", deletedRestingHR, eventId);
        }
    }
}
